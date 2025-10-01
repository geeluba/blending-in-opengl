#extension GL_OES_EGL_image_external : require
precision mediump float;

varying vec2 vTexCoord;
uniform samplerExternalOES sTexture;

// Blending uniforms
uniform bool uIsLeft;
uniform vec4 uBlendRect; // Blend rectangle in normalized screen coords (minX, minY, maxX, maxY)
uniform float uAlpha;
uniform vec2 uResolution; // View resolution (width, height)

// Convert from sRGB gamma-encoded to linear
vec3 srgbToLinear(vec3 srgb) {
    vec3 linear;
    for (int i = 0; i < 3; i++) {
        if (srgb[i] <= 0.04045) {
            linear[i] = srgb[i] / 12.92;
        } else {
            linear[i] = pow((srgb[i] + 0.055) / 1.055, 2.4);
        }
    }
    return linear;
}

// Convert from linear to sRGB gamma-encoded
vec3 linearToSrgb(vec3 linear) {
    vec3 srgb;
    for (int i = 0; i < 3; i++) {
        if (linear[i] <= 0.0031308) {
            srgb[i] = linear[i] * 12.92;
        } else {
            srgb[i] = 1.055 * pow(linear[i], 1.0 / 2.4) - 0.055;
        }
    }
    return srgb;
}


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

    // 1. Convert sRGB input to linear space for proper blending
    vec3 linearColor = srgbToLinear(textureColor.rgb);

    // 2. Apply blend factor in linear space (this is crucial for correct blending)
    vec3 blendedLinearColor = linearColor * blendFactor;

    // 3. Convert back to sRGB space for display
    vec3 finalSrgbColor = linearToSrgb(blendedLinearColor);

    // 4. Output final color with alpha
    gl_FragColor = vec4(finalSrgbColor, textureColor.a * blendFactor * uAlpha);
}