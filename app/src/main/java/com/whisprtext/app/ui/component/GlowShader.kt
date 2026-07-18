package com.whisprtext.app.ui.component

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private const val GLOW_SHADER_SRC = """
    uniform float2 size;
    uniform float time;
    uniform float4 color;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / size;
        float dist = distance(uv, float2(0.5, 0.5));
        float pulse = 0.5 + 0.5 * sin(time * 2.0);
        float glow = exp(-dist * 4.0) * pulse;
        return half4(color.rgb * glow, color.a * glow);
    }
"""

@Composable
fun Modifier.glowShader(
    color: Color = Color.White,
    enabled: Boolean = true
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !enabled) {
        return this
    }

    val shader = remember {
        try {
            android.graphics.RuntimeShader(GLOW_SHADER_SRC)
        } catch (e: Exception) {
            null
        }
    } ?: return this

    val infiniteTransition = rememberInfiniteTransition(label = "GlowShaderTransition")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "GlowShaderTime"
    )

    return this.drawWithCache {
        val brush = ShaderBrush(shader)
        
        onDrawWithContent {
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("time", time)
            shader.setFloatUniform("color", color.red, color.green, color.blue, color.alpha)
            
            drawContent()
            drawRect(brush = brush, alpha = 0.3f)
        }
    }
}
