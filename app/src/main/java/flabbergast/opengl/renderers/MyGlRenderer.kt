package flabbergast.opengl.renderers

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.GLES30
import flabbergast.opengl.primitives.Triangle

class MyGLRenderer : GLSurfaceView.Renderer {

    private lateinit var triangle: Triangle
    private var angle = 0f

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        triangle = Triangle()
    }

    override fun onDrawFrame(unused: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        angle += 1f
        triangle.draw(angle)
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }
}

