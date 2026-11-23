package ca.neathx.fx

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import java.util.Currency

/** The popup calculator: type in either field and the other converts live. */
class CalculatorActivity : Activity() {

    private lateinit var btnFrom: View
    private lateinit var btnTo: View
    private lateinit var flagFrom: ImageView
    private lateinit var flagTo: ImageView
    private lateinit var codeFrom: TextView
    private lateinit var codeTo: TextView
    private lateinit var editFrom: EditText
    private lateinit var editTo: EditText
    private lateinit var clearFrom: ImageButton
    private lateinit var clearTo: ImageButton
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
        flagFrom = findViewById(R.id.flag_from)
        flagTo = findViewById(R.id.flag_to)
        codeFrom = findViewById(R.id.code_from)
        codeTo = findViewById(R.id.code_to)
        editFrom = findViewById(R.id.edit_from)
        editTo = findViewById(R.id.edit_to)
        clearFrom = findViewById(R.id.clear_from)
        clearTo = findViewById(R.id.clear_to)
        txtRate = findViewById(R.id.txt_rate)
        txtStatus = findViewById(R.id.txt_status)

        // Round the flag buttons' corners.
        btnFrom.clipToOutline = true
        btnTo.clipToOutline = true

        btnFrom.setOnClickListener { showPicker(forFrom = true) }
        btnTo.setOnClickListener { showPicker(forFrom = false) }
        findViewById<ImageButton>(R.id.btn_swap).setOnClickListener { swap() }
        findViewById<ImageButton>(R.id.btn_refresh).setOnClickListener { refresh(showErrors = true) }
        findViewById<ImageButton>(R.id.btn_theme).setOnClickListener { showThemePicker() }
        clearFrom.setOnClickListener { clearAmounts(editFrom) }
        clearTo.setOnClickListener { clearAmounts(editTo) }

        editFrom.addTextChangedListener(watcher {
            if (!updatingFields) { lastEditedFrom = true; recalc() }
            updateClearButtons()
        })
        editTo.addTextChangedListener(watcher {
            if (!updatingFields) { lastEditedFrom = false; recalc() }
            updateClearButtons()
        })

        render()
        if (Fx.rate(state) != null && editFrom.text.isEmpty()) {
            editFrom.setText(Fx.plain(Fx.presets(state)[0]).removeSuffix(".00"))
        }
        editFrom.requestFocus()
        editFrom.selectAll()
        updateClearButtons()

        if (Fx.isStale(state)) refresh(showErrors = false)
    }

    private fun render() {
        state = Fx.load(this)
        flagFrom.setImageResource(Fx.flagRes(this, state.from))
        flagTo.setImageResource(Fx.flagRes(this, state.to))
        codeFrom.text = "${state.from} ▾"
        codeTo.text = "${state.to} ▾"
        btnFrom.contentDescription = "Convert from ${Fx.name(state.from)}. Tap to change."
        btnTo.contentDescription = "Convert to ${Fx.name(state.to)}. Tap to change."
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

    private fun updateClearButtons() {
        clearFrom.visibility = if (editFrom.text.isEmpty()) View.INVISIBLE else View.VISIBLE
        clearTo.visibility = if (editTo.text.isEmpty()) View.INVISIBLE else View.VISIBLE
    }

    /** Empties both amounts and puts the cursor back in [field], ready to type. */
    private fun clearAmounts(field: EditText) {
        updatingFields = true
        editFrom.setText("")
        editTo.setText("")
        updatingFields = false
        lastEditedFrom = field === editFrom
        updateClearButtons()
        field.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun showThemePicker() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        fun header(text: String) = TextView(this).apply {
            this.text = text
            setPadding(0, dp(12), 0, dp(4))
            textSize = 13f
            alpha = 0.7f
        }
        fun group(labels: List<String>, selected: Int): RadioGroup = RadioGroup(this).apply {
            labels.forEachIndexed { i, label ->
                addView(RadioButton(context).apply {
                    id = View.generateViewId()
                    text = label
                    isChecked = i == selected
                })
            }
        }
        fun RadioGroup.selectedIndex(): Int =
            (0 until childCount).firstOrNull { (getChildAt(it) as RadioButton).isChecked } ?: 0

        val modes = Fx.Mode.values()
        val styles = Fx.DarkStyle.values()
        val modeGroup = group(modes.map { it.label }, modes.indexOf(Fx.mode(this)))
        val styleGroup = group(styles.map { it.label }, styles.indexOf(Fx.darkStyle(this)))
        box.addView(header("Appearance"))
        box.addView(modeGroup)
        box.addView(header("Dark style"))
        box.addView(styleGroup)

        AlertDialog.Builder(this)
            .setTitle(R.string.theme)
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ ->
                Fx.setTheme(this, modes[modeGroup.selectedIndex()], styles[styleGroup.selectedIndex()])
                FxWidgetProvider.updateAll(this)
                Toast.makeText(this, "Widget theme updated", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showPicker(forFrom: Boolean) {
        val codes: Set<String> = if (state.rates.isNotEmpty()) {
            state.rates.keys
        } else {
            Currency.getAvailableCurrencies().map { it.currencyCode }.toSet()
        }
        val common = listOf("THB", "CAD", "USD", "EUR", "JPY", "GBP").filter { it in codes }
        val all = common + (codes - common.toSet()).sorted()

        val search = EditText(this).apply {
            hint = "Search, e.g. baht or JPY"
            setSingleLine()
        }
        val list = ListView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), 0)
            addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(380)))
        }

        val adapter = CurrencyAdapter(all)
        list.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (forFrom) "Convert from" else "Convert to")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .create()

        search.addTextChangedListener(watcher { q ->
            val query = q.trim()
            adapter.show(
                if (query.isEmpty()) all
                else all.filter { it.contains(query, ignoreCase = true) || Fx.name(it).contains(query, ignoreCase = true) }
            )
        })
        list.setOnItemClickListener { _, _, position, _ ->
            val code = adapter.getItem(position)
            if (forFrom) {
                setPair(code, if (code == state.to) state.from else state.to)
            } else {
                setPair(if (code == state.from) state.to else state.from, code)
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    /** Currency list rows: flag image, code, full name. */
    private inner class CurrencyAdapter(private var items: List<String>) : BaseAdapter() {
        fun show(newItems: List<String>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: LayoutInflater.from(this@CalculatorActivity)
                .inflate(R.layout.item_currency, parent, false)
            val code = items[position]
            row.findViewById<ImageView>(R.id.item_flag).setImageResource(Fx.flagRes(this@CalculatorActivity, code))
            row.findViewById<TextView>(R.id.item_code).text = code
            row.findViewById<TextView>(R.id.item_name).text = Fx.name(code)
            return row
        }
    }

    private fun watcher(onChange: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) = onChange(s?.toString().orEmpty())
    }
}
