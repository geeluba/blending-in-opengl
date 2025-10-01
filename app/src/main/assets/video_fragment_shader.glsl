#extension GL_OES_EGL_image_external : require
precision mediump float;

varying vec2 vTexCoord;
uniform samplerExternalOES sTexture;

// Blending uniforms
uniform vec4 uBlendRect; // (minX, minY, maxX, maxY) normalized
uniform float uBlendInvWidth; // 1.0 / (uBlendRect.z - uBlendRect.x)
uniform float uIsLeftFlag; // 1.0 = left projector (fade 1->0), 0.0 = right projector (0->1)
uniform float uAlpha;
uniform vec2 uResolution;

// sRGB -> Linear
vec3 srgbToLinear(vec3 c) {
    vec3 cut = step(vec3(0.04045), c);
    vec3 low = c / 12.92;
    vec3 high = pow((c + 0.055) / 1.055, vec3(2.4));
    return mix(low, high, cut);
}

// Linear -> sRGB
vec3 linearToSrgb(vec3 c) {
    vec3 cut = step(vec3(0.0031308), c);
    vec3 low = c * 12.92;
    vec3 high = 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055;
    return mix(low, high, cut);
}

void main() {
    vec4 texColor = texture2D(sTexture, vTexCoord);

    // Normalized fragment coord
    vec2 p = gl_FragCoord.xy / uResolution.xy;

    // 计算水平归一化位置 t（未裁剪）
    float t = (p.x - uBlendRect.x) * uBlendInvWidth;

    // clamp 到 [0,1]（区域外自动 0 或 1 之前再由 mask 控制）
    float tf = clamp(t, 0.0, 1.0);

    // 垂直方向是否在融合带内
    float maskY = step(uBlendRect.y, p.y) * step(p.y, uBlendRect.w);

    // 水平方向是否在融合带内
    float maskX = step(uBlendRect.x, p.x) * step(p.x, uBlendRect.z);

    float inRect = maskX * maskY;

    // 生成左右投影共用的线性权重:
    // 右侧投影 (uIsLeftFlag=1.0) 需要 (1 - tf)
    // 左侧投影 (uIsLeftFlag=0.0) 需要 tf
    float lrBlend = mix(tf, 1.0 - tf, uIsLeftFlag);

    // 区域外保持 1.0，区域内用 lrBlend
    float blendFactor = mix(1.0, lrBlend, inRect);

    // sRGB -> Linear
    vec3 linearColor = srgbToLinear(texColor.rgb);

    // Apply blend in linear
    linearColor *= blendFactor;

    // Linear -> sRGB
    vec3 finalColor = linearToSrgb(linearColor);

    gl_FragColor = vec4(finalColor, texColor.a * blendFactor * uAlpha);
}