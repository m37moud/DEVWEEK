package dev.daymark.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import dev.daymark.widgets.WidgetSnapshot
import kotlinx.coroutines.runBlocking

class WidgetPreviewStates : PreviewParameterProvider<WidgetSnapshot> {
    override val values: Sequence<WidgetSnapshot> =
        sequenceOf("AWAITING_CHECK", "SCHEDULED", "PRESENT", "DAY_OFF", "STALE")
            .map(::widgetPreviewSnapshot)
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@Preview(widthDp = 250, heightDp = 80)
@Composable
fun CompactWidgetPreview(@PreviewParameter(WidgetPreviewStates::class) snapshot: WidgetSnapshot) {
    AndroidView(factory = { context ->
        runBlocking {
            GlanceRemoteViews().compose(context, DpSize(250.dp, 80.dp)) {
                WidgetContent(context, snapshot)
            }.remoteViews.apply(context, null)
        }
    })
}
