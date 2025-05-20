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
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class PointCloudStreamRenderer(
    private val context: Context,
    private val pointCount: Int = 1000
) : GLSurfaceView.Renderer {

    data class Point(var x: Float, var y: Float, var z: Float, var speed: Float)

    private lateinit var pointList: List<Point>
    private lateinit var pointsBuffer: FloatBuffer
    private var program = 0
    private val mvpMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(1f, 1f, 1f, 1f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        program = createProgram()
        pointsBuffer = createPoints()
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 2f, 7f)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 3f, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
    }

    override fun onDrawFrame(unused: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(program)

        updatePoints()

        val positionHandle = GLES30.glGetAttribLocation(program, "vPosition")
        val matrixHandle = GLES30.glGetUniformLocation(program, "uMVPMatrix")

        GLES30.glUniformMatrix4fv(matrixHandle, 1, false, vpMatrix, 0)

        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glVertexAttribPointer(positionHandle, 3, GLES30.GL_FLOAT, false, 0, pointsBuffer)

        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, pointCount)
        GLES30.glDisableVertexAttribArray(positionHandle)
    }

    private fun createPoints(): FloatBuffer {
        pointList = List(pointCount) { i ->
            val spacing = 2.5f / pointCount // control initial vertical spacing
            val yStart = 1.5f - i * spacing

            Point(
                x = Random.nextFloat() * 2f - 1f,
                y = yStart,
                z = 0f,
                speed = Random.nextFloat() * 0.020f + 0.005f
            )
        }

        return ByteBuffer.allocateDirect(pointCount * 3 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }

    private fun updatePoints() {
        pointsBuffer.position(0)
        for (i in 0 until pointCount) {
            val point = pointList[i]

            // Move downward
            point.y -= point.speed

            // Recycle to top
            if (point.y < -1.5f) {
                point.y += 3.0f
            }

            // Inverted taper: narrow at top, wider at bottom (curved tapering)
            val normalizedY = (point.y + 1.5f) / 3.0f
            val invertedY = 1f - normalizedY
            val taperFactor = 0.35f + 0.25f * normalizedY

            val taperedX = point.x * taperFactor

            pointsBuffer.put(taperedX)
            pointsBuffer.put(point.y)
            pointsBuffer.put(point.z)
        }
        pointsBuffer.position(0)
    }

    private fun createProgram(): Int {
        val vertexShader = loadShader(
            GLES30.GL_VERTEX_SHADER, """
            uniform mat4 uMVPMatrix;
            attribute vec4 vPosition;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                gl_PointSize = 10.0;
            }
        """.trimIndent()
        )

        val fragmentShader = loadShader(
            GLES30.GL_FRAGMENT_SHADER, """
            precision mediump float;
            void main() {
                vec2 coord = gl_PointCoord * 2.0 - 1.0;
                float dist = dot(coord, coord);
                if (dist > 1.0) discard;
                gl_FragColor = vec4(0.0, 0.0, 0.5, 1.0);
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
