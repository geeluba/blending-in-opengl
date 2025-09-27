#extension GL_OES_EGL_image_external : require
precision mediump float;

varying vec2 vTexCoord;
uniform samplerExternalOES sTexture;

// Blend rectangle in normalized screen coords (minX, minY, maxX, maxY)
uniform vec4 uBlendRect;
// The alpha value to use inside the rectangle (e.g., 0.5 for 50% transparent)
uniform float uBlendAlpha;
//To hold the view's width and height
uniform vec2 uResolution;


void main() {
    vec4 videoColor = texture2D(sTexture, vTexCoord);

    // NEW: Calculate normalized screen coordinates directly from gl_FragCoord
    vec2 normalizedScreenCoord = gl_FragCoord.xy / uResolution.xy;

    // Check if the rectangle is valid (width > 0)
    if (uBlendRect.z > uBlendRect.x) {
        // Use the new, reliable coordinate for the check
        bool inRectX = normalizedScreenCoord.x > uBlendRect.x && normalizedScreenCoord.x < uBlendRect.z;

        // Note: The Y coordinate of gl_FragCoord is Y-up, same as our normalized rect.
        bool inRectY = normalizedScreenCoord.y > uBlendRect.y && normalizedScreenCoord.y < uBlendRect.w;

        if (inRectX && inRectY) {
            // Use the robust alpha assignment
            videoColor.a = uBlendAlpha;
        } else {
            videoColor.a = 1.0; // Fully opaque outside the rectangle
        }
        gl_FragColor = videoColor;
    }
}

//testing code
/*void main() {
    vec4 videoColor = texture2D(sTexture, vTexCoord);

    // TEMPORARY TEST: Force every pixel to be semi-transparent
    gl_FragColor = vec4(videoColor.rgb, 0.2);
}*/
