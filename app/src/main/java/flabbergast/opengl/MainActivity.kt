package flabbergast.opengl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import flabbergast.opengl.state.State
import flabbergast.opengl.state.changeState
import flabbergast.opengl.ui.view.SphereCloudView
import flabbergast.opengl.ui.view.StreamCloudView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(State.SPHERE) }
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    when(state){
                        State.SPHERE -> SphereCloudView(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.0f).align(Alignment.Center),
                            pointCount = 4000
                        )
                        State.STREAM -> StreamCloudView(
                            modifier = Modifier.align(Alignment.Center),
                            pointCount = 3000
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.White ,Color.White)
                                )
                            )
                            .align(Alignment.BottomCenter),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Button(
                            onClick = { state = changeState(state) },
                            modifier = Modifier.padding(bottom = 32.dp)
                        ) {
                            Text("Switch Mode")
                        }
                    }
                }
            }
        }
    }
}

