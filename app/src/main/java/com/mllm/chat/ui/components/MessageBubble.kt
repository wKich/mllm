package com.mllm.chat.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mllm.chat.data.model.Message
import com.mllm.chat.data.model.MessageRole
import com.mllm.chat.ui.theme.*
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin

@Composable
fun MessageBubble(
    message: Message,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER.value
    val context = LocalContext.current

    var showActions by remember { mutableStateOf(false) }

    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.userBubble
    } else {
        MaterialTheme.colorScheme.assistantBubble
    }

    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onUserBubble
    } else {
        MaterialTheme.colorScheme.onAssistantBubble
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(bubbleColor)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { showActions = !showActions },
                        onTap = { showActions = false }
                    )
                }
                .padding(12.dp)
        ) {
            Column {
                if (message.isStreaming) {
                    StreamingContent(
                        content = message.content,
                        textColor = textColor,
                        context = context
                    )
                } else if (message.isError) {
                    ErrorContent(
                        content = message.content,
                        onRetry = onRetry
                    )
                } else {
                    MarkdownContent(
                        content = message.content,
                        textColor = textColor,
                        context = context
                    )
                }
            }
        }

        // Action buttons
        AnimatedVisibility(visible = showActions && !message.isStreaming) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        copyToClipboard(context, message.content)
                        showActions = false
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        shareText(context, message.content)
                        showActions = false
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingContent(
    content: String,
    textColor: Color,
    context: Context
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    Row {
        MarkdownContent(
            content = content,
            textColor = textColor,
            context = context
        )
        Text(
            text = "▌",
            color = textColor.copy(alpha = alpha),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun MarkdownContent(
    content: String,
    textColor: Color,
    context: Context
) {
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .build()
    }

    val textColorArgb = textColor.toArgb()

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColorArgb)
                textSize = 16f
                setLineSpacing(4f, 1f)
            }
        },
        update = { textView ->
            textView.setTextColor(textColorArgb)
            markwon.setMarkdown(textView, content.ifEmpty { " " })
        }
    )
}

@Composable
private fun ErrorContent(
    content: String,
    onRetry: (() -> Unit)?
) {
    Column {
        Text(
            text = content,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )

        if (onRetry != null) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Retry",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retry")
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Message", text)
    clipboard.setPrimaryClip(clip)
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share message"))
}
