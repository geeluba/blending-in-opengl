#extension GL_OES_EGL_image_external : require
precision mediump float;

varying vec2 vTexCoord;
uniform samplerExternalOES sTexture;

// Blending uniforms
uniform bool uIsLeft;
uniform vec4 uBlendRect;      // Blend rectangle in normalized screen coords (minX, minY, maxX, maxY)
uniform float uGamma;
uniform float uAlpha;
uniform vec2 uResolution;     // View resolution (width, height)

void main() {
    vec4 textureColor = texture2D(sTexture, vTexCoord);
    float blendFactor = 1.0;

    // Normalize fragment coordinates to [0, 1] range
    vec2 normalizedScreenCoord = gl_FragCoord.xy / uResolution.xy;

    // Check if the fragment is inside the blend rectangle
    bool inRectX = normalizedScreenCoord.x >= uBlendRect.x && normalizedScreenCoord.x <= uBlendRect.z;
    bool inRectY = normalizedScreenCoord.y >= uBlendRect.y && normalizedScreenCoord.y <= uBlendRect.w;

    if (uBlendRect.z > uBlendRect.x && inRectX && inRectY) {
        // Calculate the blend factor based on horizontal position within the rect
        float horizontalPosInRect = (normalizedScreenCoord.x - uBlendRect.x) / (uBlendRect.z - uBlendRect.x);

        if (uIsLeft) {
            // Left projector: fade from 1.0 down to 0.0 across the blend rect
            blendFactor = 1.0 - horizontalPosInRect;
        } else {
            // Right projector: fade from 0.0 up to 1.0 across the blend rect
            blendFactor = horizontalPosInRect;
        }
    }

    // 1. Convert color to linear space
    vec3 linearColor = pow(textureColor.rgb, vec3(uGamma));

    // 2. Apply blend factor in linear space
    vec3 blendedLinearColor = linearColor * blendFactor;

    // 3. Convert back to gamma-encoded space for display
    vec3 finalGammaColor = pow(blendedLinearColor, vec3(1.0 / uGamma));

    // 4. Apply final alpha
    gl_FragColor = vec4(finalGammaColor, textureColor.a * blendFactor * uAlpha);
}