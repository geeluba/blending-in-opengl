attribute vec4 aPosition;
attribute vec2 aTexCoord;

// 我們自己計算的，用於縮放和平移的矩陣
uniform mat4 uMVPMatrix;
// SurfaceTexture提供的，用於校正紋理座標的矩陣
uniform mat4 uTexMatrix;

varying vec2 vTexCoord;

void main() {
    // 套用我們的模型-視圖-投影變換
    gl_Position = uMVPMatrix * aPosition;
    // 套用SurfaceTexture的紋理座標變換
    vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
}
