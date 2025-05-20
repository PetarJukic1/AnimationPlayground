package flabbergast.opengl.renderers

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*
import kotlin.random.Random

class PointCloudSphereRenderer(
    private val pointCount: Int = 1000
) : GLSurfaceView.Renderer {

    data class Point(
        val x: Float,
        val y: Float,
        val z: Float,
        var wigglePhaseX: Float = 0f,
        var wigglePhaseY: Float = 0f,
        var wigglePhaseZ: Float = 0f,
        var frozen: Boolean = false,
        var frozenAtScale: Float = 1f,
        var frozenAtTime: Float = 0f,
        var alpha: Float = 1f,
        var frozenSincePulse: Int = 0,
        var shouldFadeWhileActive: Boolean = false,
        var activeFadePhase: Float = 0f,
        var fadeDelay: Float = 0f,
        var fadeDuration: Float = 0f,
        var ejectionSpeed: Float = 0f,
        var ejectionTime: Float = 0f,
        var ejectFromX: Float = 0f,
        var ejectFromY: Float = 0f,
        var ejectFromZ: Float = 0f,
        var hasEjected: Boolean = false,
        var shouldFade: Boolean = false
    )

    private lateinit var pointList: List<Point>
    private lateinit var pointsBuffer: FloatBuffer
    private var program = 0
    private val mvpMatrix = FloatArray(16)
    private var angle = 0f

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelViewProjectionMatrix = FloatArray(16)

    private var globalTime = 0f
    private var baseScale = 1f
    private var pulseScale = 1f
    private var pulseCount = 0

    private var previousRawPulse = 0f // Track the previous raw pulse value
    private var peakThreshold = 0.95f // Define a threshold

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(1f, 1f, 1f, 1f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        pointsBuffer = ByteBuffer.allocateDirect(pointCount * 4 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        createSpherePoints()
        program = createProgram()
    }

    override fun onDrawFrame(unused: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(program)

        globalTime += 0.02f

        baseScale = max(0f, 1f - globalTime * 0.05f)
        val rawPulse = sin(globalTime * 3f)
        pulseScale = 1f + 0.1f * rawPulse

        // Detect the pulse peak
        if (rawPulse >= peakThreshold && previousRawPulse < peakThreshold && baseScale > 0f) {
            pulseCount++
            freezeSomeLivePoints()
        }
        previousRawPulse = rawPulse // Update previousRawPulse

        pointsBuffer.position(0)

        for (point in pointList) {
            val scale = if (point.frozen) point.frozenAtScale else baseScale * pulseScale

            var (x, y, z) = if (!point.frozen) {
                if (point.shouldFadeWhileActive) {
                    val pulse = sin(globalTime * 3f + point.activeFadePhase)
                    val t = (pulse + 1f) / 2f // maps [-1,1] to [0,1]
                    point.alpha = 1f - t.pow(12f) // harsh drop-off, fades to 0 fast, then stays low briefly
                } else {
                    point.alpha = 1f
                }


//                point.alpha = 1f // No fading for now

                // Convert to spherical coordinates
                val r = 1f
                val theta = atan2(point.y, point.x)
                val phi = acos(point.z / r)

                // Apply slight time-based movement to angles
                val thetaWiggle = theta + 0.1f * sin(globalTime + point.wigglePhaseX)
                val phiWiggle = phi + 0.2f * cos(globalTime + point.wigglePhaseY)

                // Convert back to Cartesian
                val x = (r * sin(phiWiggle) * cos(thetaWiggle)) * scale
                val y = (r * sin(phiWiggle) * sin(thetaWiggle)) * scale
                val z = (r * cos(phiWiggle)) * scale

                Triple(x, y, z)
            } else {
                // FROZEN point logic
                val timeSinceEjection = (globalTime - point.ejectionTime).coerceAtLeast(0f)

                val ex = point.ejectFromX
                val ey = point.ejectFromY
                val ez = point.ejectFromZ

                val elen = sqrt(ex * ex + ey * ey + ez * ez)
                val edx = if (elen > 1e-6) -ex / elen else 0f
                val edy = if (elen > 1e-6) -ey / elen else 0f
                val edz = if (elen > 1e-6) -ez / elen else 0f

                val maxEjectionTime = 1.5f
                val t = (timeSinceEjection / maxEjectionTime).coerceIn(0f, 1f)
                val ejectionOffset = point.ejectionSpeed * t

                val fx = ex * point.frozenAtScale + edx * ejectionOffset
                val fy = ey * point.frozenAtScale + edy * ejectionOffset
                val fz = ez * point.frozenAtScale + edz * ejectionOffset

                // Add subtle drift or jitter to frozen points
                val frozenWiggleStrength = 0.05f
                val jitter = sin(globalTime * 20f + point.wigglePhaseX * 13.7f) * 0.005f
                val wiggleX = sin(globalTime * 2.1f + point.wigglePhaseX + jitter) * frozenWiggleStrength
                val wiggleY = cos(globalTime * 2.7f + point.wigglePhaseY) * frozenWiggleStrength
                val wiggleZ = sin(globalTime * 1.9f + point.wigglePhaseZ) * frozenWiggleStrength

                // Fade out logic
                val fadeElapsed = globalTime - point.frozenAtTime - point.fadeDelay
                val fadeT = (fadeElapsed / point.fadeDuration).coerceIn(0f, 1f)
                if (fadeT > 0f) {
                    point.alpha = 1f - fadeT
                }
                if (fadeT >= 1f) {
                    point.alpha = 0f
                }

                Triple(
                    fx + wiggleX,
                    fy + wiggleY,
                    fz + wiggleZ
                )
            }

            pointsBuffer.put(x)
            pointsBuffer.put(y)
            pointsBuffer.put(z)
            pointsBuffer.put(point.alpha)
        }

        pointsBuffer.position(0)

        val positionHandle = GLES30.glGetAttribLocation(program, "vPosition")
        val alphaHandle = GLES30.glGetAttribLocation(program, "aAlpha")
        val matrixHandle = GLES30.glGetUniformLocation(program, "uMVPMatrix")

        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glVertexAttribPointer(positionHandle, 3, GLES30.GL_FLOAT, false, 4 * 4, pointsBuffer)

        pointsBuffer.position(3)
        GLES30.glEnableVertexAttribArray(alphaHandle)
        GLES30.glVertexAttribPointer(alphaHandle, 1, GLES30.GL_FLOAT, false, 4 * 4, pointsBuffer)

        val modelMatrix = FloatArray(16)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.scaleM(modelMatrix, 0, 1.2f, 1.2f, 1.2f)

        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(matrixHandle, 1, false, modelViewProjectionMatrix, 0)

        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, pointCount)

        GLES30.glDisableVertexAttribArray(positionHandle)
        GLES30.glDisableVertexAttribArray(alphaHandle)

        angle += 0.4f
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)

        val ratio = width.toFloat() / height
        // Adjust the near/far and FOV to prevent clipping
        Matrix.perspectiveM(projectionMatrix, 0, 60f, ratio, 0.1f, 100f)

        // Set a camera/view matrix looking from a distance
        Matrix.setLookAtM(viewMatrix, 0,
            0f, 0f, 4f, // eye position (further away helps avoid clipping)
            0f, 0f, 0f, // look at center
            0f, 1f, 0f  // up vector
        )
    }

    private fun createSpherePoints() {
        val list = mutableListOf<Point>()
        val rand = Random(System.currentTimeMillis())
        val randPhase = { rand.nextFloat() * (2f * PI).toFloat() }

        val maxAttempts = pointCount * 10
        var attempts = 0

        while (list.size < pointCount && attempts < maxAttempts) {
            attempts++

            // Uniform spherical sampling
            val u = rand.nextFloat()
            val v = rand.nextFloat()
            val theta = 2f * PI.toFloat() * u
            val phi = acos(2f * v - 1f)

            val x = (sin(phi) * cos(theta)).toFloat()
            val y = (sin(phi) * sin(theta)).toFloat()
            val z = cos(phi).toFloat()

            // Projected radius: 0 (center) to 1 (rim)
            val r = sqrt(x * x + y * y)

            // Bias density toward rim but allow all areas
            // Acceptance curve: keep most outer, fewer inner
            // Change the exponent to make the bias stronger or softer
            val biasPower = 2.5f
            val baseAcceptance = 0.35f + 0.4f * (1f - pulseScale) // less center density on exhale
            val acceptance = baseAcceptance + (1f - baseAcceptance) * r.pow(biasPower)

            if (rand.nextFloat() < acceptance) {
                list.add(
                    Point(
                        x = x,
                        y = y,
                        z = z,
                        wigglePhaseX = randPhase(),
                        wigglePhaseY = randPhase(),
                        wigglePhaseZ = randPhase(),
                        shouldFadeWhileActive = rand.nextFloat() < 0.50f, // 25% of points fade while active
                        activeFadePhase = rand.nextFloat() * (2f * PI).toFloat()
                    )
                )
            }
        }

        pointList = list
    }

    private fun freezeSomeLivePoints() {
        val eligible = pointList.filter { !it.frozen && !it.hasEjected }
        val freezeHalfAmount = (eligible.size / 2.0).roundToInt()
        if (eligible.isEmpty()) return

        val freezeCount = if(freezeHalfAmount < pointCount / 8) eligible.size else freezeHalfAmount

        eligible.shuffled().take(freezeCount).forEach { point ->
            point.hasEjected = true
            point.frozen = true
            point.frozenAtScale = baseScale * pulseScale
            point.alpha = 1f
            point.frozenSincePulse = pulseCount
            point.frozenAtTime = globalTime
            point.fadeDelay = Random.nextFloat() * 3f + 0.2f
            point.fadeDuration = Random.nextFloat() * 4f + 0.6f
            point.ejectFromX = point.x
            point.ejectFromY = point.y
            point.ejectFromZ = point.z
            val currentPulseAmplitude = pulseScale - 1f
            point.ejectionSpeed = -(0.05f + currentPulseAmplitude * 1.5f + Random.nextFloat() * 0.5f)
            point.ejectionTime = globalTime
        }
    }


    private fun createProgram(): Int {
        val vertexShader = loadShader(
            GLES30.GL_VERTEX_SHADER, """
            attribute vec4 vPosition;
            attribute float aAlpha;
            uniform mat4 uMVPMatrix;
            
            varying float vAlpha;
            varying vec2 vUv;
            
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                gl_PointSize = 8.0;
            
                // Generate UV coordinates from position
                vUv = vec2((vPosition.x + 1.0) * 0.5, (vPosition.y + 1.0) * 0.5);
            
                vAlpha = aAlpha;
            }

        """.trimIndent()
        )

        val fragmentShader = loadShader(
            GLES30.GL_FRAGMENT_SHADER, """
                precision mediump float;

                varying float vAlpha;
                varying vec2 vUv;
                
                uniform float uTime;
                
                // --- Perlin noise helpers from Gustavson ---
                vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
                vec4 mod289(vec4 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
                vec4 permute(vec4 x) { return mod289(((x * 34.0) + 10.0) * x); }
                vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }
                vec3 fade(vec3 t) { return t * t * t * (t * (t * 6.0 - 15.0) + 10.0); }
                
                float perlin3dPeriodic(vec3 P, vec3 rep) {
                    vec3 Pi0 = mod(floor(P), rep);
                    vec3 Pi1 = mod(Pi0 + vec3(1.0), rep);
                    Pi0 = mod289(Pi0);
                    Pi1 = mod289(Pi1);
                    vec3 Pf0 = fract(P);
                    vec3 Pf1 = Pf0 - vec3(1.0);
                    vec4 ix = vec4(Pi0.x, Pi1.x, Pi0.x, Pi1.x);
                    vec4 iy = vec4(Pi0.yy, Pi1.yy);
                    vec4 iz0 = Pi0.zzzz;
                    vec4 iz1 = Pi1.zzzz;
                
                    vec4 ixy = permute(permute(ix) + iy);
                    vec4 ixy0 = permute(ixy + iz0);
                    vec4 ixy1 = permute(ixy + iz1);
                
                    vec4 gx0 = ixy0 * (1.0 / 7.0);
                    vec4 gy0 = fract(floor(gx0) * (1.0 / 7.0)) - 0.5;
                    gx0 = fract(gx0);
                    vec4 gz0 = vec4(0.5) - abs(gx0) - abs(gy0);
                    vec4 sz0 = step(gz0, vec4(0.0));
                    gx0 -= sz0 * (step(0.0, gx0) - 0.5);
                    gy0 -= sz0 * (step(0.0, gy0) - 0.5);
                
                    vec4 gx1 = ixy1 * (1.0 / 7.0);
                    vec4 gy1 = fract(floor(gx1) * (1.0 / 7.0)) - 0.5;
                    gx1 = fract(gx1);
                    vec4 gz1 = vec4(0.5) - abs(gx1) - abs(gy1);
                    vec4 sz1 = step(gz1, vec4(0.0));
                    gx1 -= sz1 * (step(0.0, gx1) - 0.5);
                    gy1 -= sz1 * (step(0.0, gy1) - 0.5);
                
                    vec3 g000 = vec3(gx0.x, gy0.x, gz0.x);
                    vec3 g100 = vec3(gx0.y, gy0.y, gz0.y);
                    vec3 g010 = vec3(gx0.z, gy0.z, gz0.z);
                    vec3 g110 = vec3(gx0.w, gy0.w, gz0.w);
                    vec3 g001 = vec3(gx1.x, gy1.x, gz1.x);
                    vec3 g101 = vec3(gx1.y, gy1.y, gz1.y);
                    vec3 g011 = vec3(gx1.z, gy1.z, gz1.z);
                    vec3 g111 = vec3(gx1.w, gy1.w, gz1.w);
                
                    vec4 norm0 = taylorInvSqrt(vec4(dot(g000, g000), dot(g010, g010), dot(g100, g100), dot(g110, g110)));
                    g000 *= norm0.x;
                    g010 *= norm0.y;
                    g100 *= norm0.z;
                    g110 *= norm0.w;
                    vec4 norm1 = taylorInvSqrt(vec4(dot(g001, g001), dot(g011, g011), dot(g101, g101), dot(g111, g111)));
                    g001 *= norm1.x;
                    g011 *= norm1.y;
                    g101 *= norm1.z;
                    g111 *= norm1.w;
                
                    float n000 = dot(g000, Pf0);
                    float n100 = dot(g100, vec3(Pf1.x, Pf0.yz));
                    float n010 = dot(g010, vec3(Pf0.x, Pf1.y, Pf0.z));
                    float n110 = dot(g110, vec3(Pf1.xy, Pf0.z));
                    float n001 = dot(g001, vec3(Pf0.xy, Pf1.z));
                    float n101 = dot(g101, vec3(Pf1.x, Pf0.y, Pf1.z));
                    float n011 = dot(g011, vec3(Pf0.x, Pf1.yz));
                    float n111 = dot(g111, Pf1);
                
                    vec3 fade_xyz = fade(Pf0);
                    vec4 n_z = mix(vec4(n000, n100, n010, n110), vec4(n001, n101, n011, n111), fade_xyz.z);
                    vec2 n_yz = mix(n_z.xy, n_z.zw, fade_xyz.y);
                    float n_xyz = mix(n_yz.x, n_yz.y, fade_xyz.x);
                    return 2.2 * n_xyz;
                }
                // --- End Perlin ---
                
                void main() {
                    // Discard non-circle area
                    vec2 coord = gl_PointCoord * 2.0 - 1.0;
                    if (dot(coord, coord) > 1.0) discard;
                    
                    vec2 jitteredUV = vUv + 0.03 * vec2(
                        sin(uTime * 1.3 + vUv.y * 10.0),
                        cos(uTime * 1.7 + vUv.x * 10.0)
                    );
                
                    float perlin1 = perlin3dPeriodic(vec3(jitteredUV * 5.0, uTime * 0.25), vec3(5.0));
                    float perlin2 = perlin3dPeriodic(vec3(jitteredUV * 10.0, uTime * 0.35), vec3(10.0));
                    float perlin3 = perlin3dPeriodic(vec3(jitteredUV * 20.0, uTime * 0.45), vec3(20.0));
                    float perlin4 = perlin3dPeriodic(vec3(jitteredUV * 40.0, uTime * 0.55), vec3(40.0));

                    float r_channel =  0.0;
                    float g_channel =  0.36;
                    float b_channel =  1.0;
                    float alpha = min(perlin4, vAlpha);
                    
                    gl_FragColor = vec4(r_channel, g_channel, b_channel, alpha);
                }
            """.trimIndent()
        )

        return GLES30.glCreateProgram().also {
            GLES30.glAttachShader(it, vertexShader)
            GLES30.glAttachShader(it, fragmentShader)
            GLES30.glLinkProgram(it)
        }
    }

    private fun loadShader(type: Int, code: String): Int {
        return GLES30.glCreateShader(type).also { shader ->
            GLES30.glShaderSource(shader, code)
            GLES30.glCompileShader(shader)
        }
    }
}
