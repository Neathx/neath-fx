package ca.neathx.fx

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class FxWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
        if (Fx.isStale(Fx.load(context))) refreshAsync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_SWAP -> {
                Fx.swap(context)
                updateAll(context)
            }
            ACTION_REFRESH -> refreshAsync(context)
        }
    }

    private fun refreshAsync(context: Context) {
        val pending = goAsync()
        val app = context.applicationContext
        Fx.setLoading(app, true)
        updateAll(app)
        Thread {
            try {
                Fx.fetch(app)
            } finally {
                Fx.setLoading(app, false)
                updateAll(app)
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_SWAP = "ca.neathx.fx.action.SWAP"
        const val ACTION_REFRESH = "ca.neathx.fx.action.REFRESH"

        private val AMOUNT_IDS = intArrayOf(R.id.amt1, R.id.amt2, R.id.amt3, R.id.amt4)
        private val VALUE_IDS = intArrayOf(R.id.val1, R.id.val2, R.id.val3, R.id.val4)

        /** Redraws every placed widget from the saved state. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FxWidgetProvider::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, build(context))
        }

        private fun build(context: Context): RemoteViews {
            val s = Fx.load(context)
            val v = RemoteViews(context.packageName, R.layout.widget_fx)

            v.setTextViewText(R.id.pair, "${Fx.flag(s.from)} ${s.from}  →  ${Fx.flag(s.to)} ${s.to}")

            val r = Fx.rate(s)
            if (r == null) {
                v.setTextViewText(R.id.rate, "Rates not loaded yet")
                for (i in AMOUNT_IDS.indices) {
                    v.setTextViewText(AMOUNT_IDS[i], "—")
                    v.setTextViewText(VALUE_IDS[i], "")
                }
            } else {
                v.setTextViewText(R.id.rate, "1 ${s.from} = ${Fx.fmt(r)} ${s.to}")
                val presets = Fx.presets(s)
                for (i in AMOUNT_IDS.indices) {
                    val amount = presets[i]
                    v.setTextViewText(AMOUNT_IDS[i], "${Fx.fmtAmount(amount)} ${s.from}")
                    v.setTextViewText(VALUE_IDS[i], "${Fx.fmt(amount * r)} ${s.to}")
                }
            }
            v.setTextViewText(R.id.status, Fx.status(s))

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, CalculatorActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                flags,
            )
            v.setOnClickPendingIntent(R.id.root, open)
            v.setOnClickPendingIntent(R.id.table, open)
            v.setOnClickPendingIntent(R.id.btn_swap, broadcast(context, ACTION_SWAP, 1, flags))
            v.setOnClickPendingIntent(R.id.btn_refresh, broadcast(context, ACTION_REFRESH, 2, flags))
            return v
        }

        private fun broadcast(context: Context, action: String, code: Int, flags: Int) =
            PendingIntent.getBroadcast(
                context, code,
                Intent(context, FxWidgetProvider::class.java).setAction(action),
                flags,
            )
    }
}
