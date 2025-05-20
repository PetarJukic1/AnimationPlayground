package flabbergast.opengl.renderers

import android.content.Context
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
    private val context: Context,
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
        val vertexShaderCode = readShaderFileFromAssets("shaders/vertex/point_cloud_vertex.glsl")
        val fragmentShaderCode = readShaderFileFromAssets("shaders/fragment/point_cloud_fragment.glsl")

        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderCode)

        return GLES30.glCreateProgram().also {
            GLES30.glAttachShader(it, vertexShader)
            GLES30.glAttachShader(it, fragmentShader)
            GLES30.glLinkProgram(it)
        }
    }

    private fun readShaderFileFromAssets(filename: String): String {
        return context.assets.open(filename).bufferedReader().use { it.readText() }
    }


    private fun loadShader(type: Int, code: String): Int {
        return GLES30.glCreateShader(type).also { shader ->
            GLES30.glShaderSource(shader, code)
            GLES30.glCompileShader(shader)
        }
    }
}
