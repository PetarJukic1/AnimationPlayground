package flabbergast.opengl.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import flabbergast.opengl.renderers.PointCloudStreamRenderer
import flabbergast.opengl.surface.SurfaceView

@Composable
fun StreamCloudView(
    modifier: Modifier,
    pointCount: Int = 1500,
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context, PointCloudStreamRenderer(pointCount))
        },
    modifier = modifier.fillMaxSize().background(Color.White))
}