#extension GL_OES_EGL_image_external : require
precision mediump float;

varying vec2 vTexCoord;
// 這是來自SurfaceTexture的特殊紋理採樣器
uniform samplerExternalOES sTexture;

void main() {
    gl_FragColor = texture2D(sTexture, vTexCoord);
}
