package flabbergast.opengl.state

enum class State{
    SPHERE, STREAM,
}

fun changeState(state: State)= when(state){
    State.SPHERE -> State.STREAM
    State.STREAM -> State.SPHERE
}