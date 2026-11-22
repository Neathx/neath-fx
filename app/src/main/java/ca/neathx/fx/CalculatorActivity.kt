package ca.neathx.fx

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.Currency

/** The popup calculator: type in either field and the other converts live. */
class CalculatorActivity : Activity() {

    private lateinit var btnFrom: Button
    private lateinit var btnTo: Button
    private lateinit var editFrom: EditText
    private lateinit var editTo: EditText
    private lateinit var txtRate: TextView
    private lateinit var txtStatus: TextView

    private lateinit var state: Fx.State
    private var updatingFields = false
    private var lastEditedFrom = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calculator)

        btnFrom = findViewById(R.id.btn_from)
        btnTo = findViewById(R.id.btn_to)
        editFrom = findViewById(R.id.edit_from)
        editTo = findViewById(R.id.edit_to)
        txtRate = findViewById(R.id.txt_rate)
        txtStatus = findViewById(R.id.txt_status)

        btnFrom.setOnClickListener { showPicker(forFrom = true) }
        btnTo.setOnClickListener { showPicker(forFrom = false) }
        findViewById<ImageButton>(R.id.btn_swap).setOnClickListener { swap() }
        findViewById<ImageButton>(R.id.btn_refresh).setOnClickListener { refresh(showErrors = true) }

        editFrom.addTextChangedListener(watcher {
            if (!updatingFields) { lastEditedFrom = true; recalc() }
        })
        editTo.addTextChangedListener(watcher {
            if (!updatingFields) { lastEditedFrom = false; recalc() }
        })

        render()
        if (Fx.rate(state) != null && editFrom.text.isEmpty()) {
            editFrom.setText(Fx.plain(Fx.presets(state)[0]).removeSuffix(".00"))
        }
        editFrom.requestFocus()
        editFrom.selectAll()

        if (Fx.isStale(state)) refresh(showErrors = false)
    }

    private fun render() {
        state = Fx.load(this)
        btnFrom.text = "${Fx.flag(state.from)}  ${state.from}  ▾"
        btnTo.text = "${Fx.flag(state.to)}  ${state.to}  ▾"
        val r = Fx.rate(state)
        txtRate.text = if (r == null) {
            "Rates not loaded yet. Tap ↻ when you're online."
        } else {
            "1 ${state.from} = ${Fx.fmt(r)} ${state.to}\n1 ${state.to} = ${Fx.fmt(1 / r)} ${state.from}"
        }
        txtStatus.text = Fx.status(state)
        recalc()
    }

    private fun recalc() {
        val r = Fx.rate(state) ?: return
        updatingFields = true
        if (lastEditedFrom) {
            val v = Fx.parse(editFrom.text.toString())
            editTo.setText(if (v == null) "" else Fx.plain(v * r))
        } else {
            val v = Fx.parse(editTo.text.toString())
            editFrom.setText(if (v == null) "" else Fx.plain(v / r))
        }
        updatingFields = false
    }

    private fun swap() {
        Fx.swap(this)
        // Keep the number you typed and move it to the new "from" side.
        val typed = if (lastEditedFrom) editFrom.text.toString() else editTo.text.toString()
        updatingFields = true
        editFrom.setText(typed)
        updatingFields = false
        lastEditedFrom = true
        render()
        FxWidgetProvider.updateAll(this)
    }

    private fun setPair(from: String, to: String) {
        Fx.setPair(this, from, to)
        render()
        FxWidgetProvider.updateAll(this)
    }

    private fun refresh(showErrors: Boolean) {
        val app = applicationContext
        Fx.setLoading(app, true)
        txtStatus.text = Fx.status(Fx.load(app))
        FxWidgetProvider.updateAll(app)
        Thread {
            val ok = Fx.fetch(app)
            Fx.setLoading(app, false)
            FxWidgetProvider.updateAll(app)
            runOnUiThread {
                if (!isDestroyed) {
                    render()
                    if (!ok && showErrors) {
                        Toast.makeText(this, "Couldn't update. Using saved rates.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    private fun showPicker(forFrom: Boolean) {
        val codes: Set<String> = if (state.rates.isNotEmpty()) {
            state.rates.keys
        } else {
            Currency.getAvailableCurrencies().map { it.currencyCode }.toSet()
        }
        val common = listOf("THB", "CAD", "USD", "EUR", "JPY", "GBP").filter { it in codes }
        val ordered = common + (codes - common.toSet()).sorted()
        val items = ordered.map { it to "${Fx.flag(it)}  $it  ·  ${Fx.name(it)}" }

        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val search = EditText(this).apply {
            hint = "Search, e.g. baht or JPY"
            setSingleLine()
        }
        val list = ListView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (380 * density).toInt()))
        }

        var filtered = items
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList(items.map { it.second }))
        list.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (forFrom) "Convert from" else "Convert to")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .create()

        search.addTextChangedListener(watcher { q ->
            val query = q.trim()
            filtered = if (query.isEmpty()) items else items.filter { it.second.contains(query, ignoreCase = true) }
            adapter.clear()
            adapter.addAll(filtered.map { it.second })
        })
        list.setOnItemClickListener { _, _, position, _ ->
            val code = filtered[position].first
            if (forFrom) {
                setPair(code, if (code == state.to) state.from else state.to)
            } else {
                setPair(if (code == state.from) state.to else state.from, code)
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun watcher(onChange: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) = onChange(s?.toString().orEmpty())
    }
}
