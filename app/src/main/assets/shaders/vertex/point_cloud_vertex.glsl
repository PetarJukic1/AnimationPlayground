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