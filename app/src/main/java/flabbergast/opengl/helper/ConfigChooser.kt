package flabbergast.opengl.helper

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

class ConfigChooser : GLSurfaceView.EGLConfigChooser {

    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig? {
        val attribList = intArrayOf(
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, 16, // Request a 16-bit depth buffer
            EGL10.EGL_STENCIL_SIZE, 0, // Don't require a stencil buffer
            EGL10.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
            EGL10.EGL_NONE // Termination flag
        )

        val numConfigs = IntArray(1)
        val configs = arrayOfNulls<EGLConfig>(1)

        if (!egl.eglChooseConfig(display, attribList, configs, 1, numConfigs)) {
            return null
        }

        if (numConfigs[0] > 0) {
            return configs[0]
        }

        return null
    }
}