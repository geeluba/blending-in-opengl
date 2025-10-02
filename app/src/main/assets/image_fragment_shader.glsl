precision mediump float;

varying vec2 vTexCoord;
uniform sampler2D sTexture;

// Blending uniforms
uniform bool uIsLeft;
uniform vec4 uBlendRect;      // Blend rectangle in normalized screen coords (minX, minY, maxX, maxY)
uniform float uGamma;
uniform float uAlpha;
uniform vec2 uResolution;     // View resolution (width, height)

const float PI = 3.14159265359;

void main() {
    vec4 textureColor = texture2D(sTexture, vTexCoord);
    float blendFactor = 1.0;

    // Normalize fragment coordinates to [0, 1] range
    vec2 normalizedScreenCoord = gl_FragCoord.xy / uResolution.xy;

    // Check if the fragment is inside the blend rectangle
    //.x = The left edge of the rectangle.
    //.y = The bottom edge of the rectangle.
    //.z = The right edge of the rectangle.
    //.w = The top edge of the rectangle.

    bool inRectX = normalizedScreenCoord.x >= uBlendRect.x && normalizedScreenCoord.x <= uBlendRect.z;
    bool inRectY = normalizedScreenCoord.y >= uBlendRect.y && normalizedScreenCoord.y <= uBlendRect.w;

    if (uBlendRect.z > uBlendRect.x && inRectX && inRectY) {
        // --- MODIFIED FOR EQUAL-POWER BLENDING ---
        // Calculate the position within the blend rect (0.0 to 1.0)
        float horizontalPosInRect = (normalizedScreenCoord.x - uBlendRect.x) / (uBlendRect.z - uBlendRect.x);

        // Convert the linear position to an angle from 0 to PI/2
        float theta = horizontalPosInRect * PI / 2.0;

        if (uIsLeft) {
            // Left projector: fade using a cosine curve for a smooth, perceptually linear falloff.
            blendFactor = cos(theta);
        } else {
            // Right projector: fade using a sine curve.
            blendFactor = sin(theta);
        }
    }

    // 1. Convert color to linear space
    vec3 linearColor = pow(textureColor.rgb, vec3(uGamma));

    // 2. Apply blend factor in linear space
    vec3 blendedLinearColor = linearColor * blendFactor;

    // 3. Convert back to gamma-encoded space for display
    vec3 finalGammaColor = pow(blendedLinearColor, vec3(1.0 / uGamma));

    // 4. Apply final alpha
    gl_FragColor = vec4(finalGammaColor, textureColor.a * uAlpha);
}