package flabbergast.opengl.surface

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import flabbergast.opengl.helper.ConfigChooser

@SuppressLint("ViewConstructor")
class SurfaceView(
    context: Context,
    renderer: Renderer
) : GLSurfaceView(context) {

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(ConfigChooser())
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(false)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}