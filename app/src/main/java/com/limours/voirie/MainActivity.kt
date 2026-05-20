package com.limours.voirie

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // ── VUES ──
    private lateinit var tvCount: TextView
    private lateinit var btnExport: Button
    private lateinit var tabSaisie: TextView
    private lateinit var tabListe: TextView
    private lateinit var pageSaisie: ScrollView
    private lateinit var pageListe: LinearLayout
    private lateinit var btnGps: Button
    private lateinit var tvGpsResult: TextView
    private lateinit var etRue: EditText
    private lateinit var etNum: EditText
    private lateinit var gridTypes: GridLayout
    private lateinit var etAutre: EditText
    private lateinit var layoutGravite: LinearLayout
    private lateinit var photoZone: LinearLayout
    private lateinit var imgPreview: ImageView
    private lateinit var btnDelPhoto: Button
    private lateinit var etComment: EditText
    private lateinit var btnSave: Button
    private lateinit var tvHint: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvGraves: TextView
    private lateinit var tvAujourd: TextView
    private lateinit var listeContent: LinearLayout

    // ── ÉTAT ──
    private var gLat = ""
    private var gLon = ""
    private val selTypes = mutableListOf<String>()
    private var selGrav = 0
    private var photoPath = ""
    private var currentPhotoUri: Uri? = null

    private val TYPES = listOf(
        "Nid-de-poule", "Fissure / faïençage", "Affaissement",
        "Glissière / bordure", "Marquage effacé", "Panneau manquant",
        "Accotement dégradé", "Autre"
    )
    private val typeBtns = mutableListOf<Button>()
    private val gravBtns = mutableListOf<Button>()

    private val PERM_LOC = 101
    private val PERM_CAM = 102
    private val REQ_PHOTO = 201

    // ── COULEURS GRAVITÉ ──
    private val gravColors = listOf(
        Pair("#0f6e56", "#e1f5ee"),
        Pair("#3b6d11", "#eaf3de"),
        Pair("#854f0b", "#faeeda"),
        Pair("#993c1d", "#faece7"),
        Pair("#a32d2d", "#fcebeb")
    )
    private val gravLabels = listOf("Mineur", "Modéré", "Sérieux", "Grave", "Très grave")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        buildTypeGrid()
        buildGravRow()
        setupListeners()
        updateHeader()
    }

    private fun bindViews() {
        tvCount      = findViewById(R.id.tvCount)
        btnExport    = findViewById(R.id.btnExport)
        tabSaisie    = findViewById(R.id.tabSaisie)
        tabListe     = findViewById(R.id.tabListe)
        pageSaisie   = findViewById(R.id.pageSaisie)
        pageListe    = findViewById(R.id.pageListe)
        btnGps       = findViewById(R.id.btnGps)
        tvGpsResult  = findViewById(R.id.tvGpsResult)
        etRue        = findViewById(R.id.etRue)
        etNum        = findViewById(R.id.etNum)
        gridTypes    = findViewById(R.id.gridTypes)
        etAutre      = findViewById(R.id.etAutre)
        layoutGravite = findViewById(R.id.layoutGravite)
        photoZone    = findViewById(R.id.photoZone)
        imgPreview   = findViewById(R.id.imgPreview)
        btnDelPhoto  = findViewById(R.id.btnDelPhoto)
        etComment    = findViewById(R.id.etComment)
        btnSave      = findViewById(R.id.btnSave)
        tvHint       = findViewById(R.id.tvHint)
        tvTotal      = findViewById(R.id.tvTotal)
        tvGraves     = findViewById(R.id.tvGraves)
        tvAujourd    = findViewById(R.id.tvAujourd)
        listeContent = findViewById(R.id.listeContent)
    }

    // ── GRILLE TYPES ──
    private fun buildTypeGrid() {
        val dp = resources.displayMetrics.density
        TYPES.forEachIndexed { idx, label ->
            val btn = Button(this).apply {
                text = label
                textSize = 13f
                setTextColor(Color.parseColor("#1a1a18"))
                setPadding((10*dp).toInt(), (10*dp).toInt(), (10*dp).toInt(), (10*dp).toInt())
                isAllCaps = false
                tag = label
                background = makeTypeDrawable(false)
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(idx % 2, 1, 1f)
                rowSpec = GridLayout.spec(idx / 2)
                setMargins((4*dp).toInt(), (4*dp).toInt(), (4*dp).toInt(), (4*dp).toInt())
            }
            btn.layoutParams = params
            btn.setOnClickListener { toggleType(btn, label) }
            gridTypes.addView(btn)
            typeBtns.add(btn)
        }
    }

    private fun makeTypeDrawable(selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10 * resources.displayMetrics.density
            if (selected) {
                setColor(Color.parseColor("#e1f5ee"))
                setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#0f6e56"))
            } else {
                setColor(Color.parseColor("#f4f4f2"))
                setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#dddbd4"))
            }
        }
    }

    private fun toggleType(btn: Button, label: String) {
        if (selTypes.contains(label)) {
            selTypes.remove(label)
            btn.background = makeTypeDrawable(false)
            btn.setTextColor(Color.parseColor("#1a1a18"))
        } else {
            selTypes.add(label)
            btn.background = makeTypeDrawable(true)
            btn.setTextColor(Color.parseColor("#0f6e56"))
        }
        etAutre.visibility = if (selTypes.contains("Autre")) android.view.View.VISIBLE else android.view.View.GONE
        checkForm()
    }

    // ── BOUTONS GRAVITÉ ──
    private fun buildGravRow() {
        val dp = resources.displayMetrics.density
        for (i in 1..5) {
            val btn = Button(this).apply {
                val lbl = gravLabels[i-1]
                text = "$i\n$lbl"
                textSize = 14f
                isAllCaps = false
                setPadding((4*dp).toInt(), (10*dp).toInt(), (4*dp).toInt(), (10*dp).toInt())
                background = makeGravDrawable(i, false)
                setTextColor(Color.parseColor("#6b6b67"))
                tag = i
            }
            val params = LinearLayout.LayoutParams(0, (60*dp).toInt(), 1f).apply {
                setMargins((3*dp).toInt(), 0, (3*dp).toInt(), 0)
            }
            btn.layoutParams = params
            btn.setOnClickListener { selectGrav(btn, i) }
            layoutGravite.addView(btn)
            gravBtns.add(btn)
        }
    }

    private fun makeGravDrawable(g: Int, selected: Boolean): GradientDrawable {
        val dp = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10 * dp
            if (selected) {
                setColor(Color.parseColor(gravColors[g-1].second))
                setStroke((2*dp).toInt(), Color.parseColor(gravColors[g-1].first))
            } else {
                setColor(Color.parseColor("#f4f4f2"))
                setStroke((2*dp).toInt(), Color.parseColor("#dddbd4"))
            }
        }
    }

    private fun selectGrav(btn: Button, g: Int) {
        gravBtns.forEachIndexed { idx, b ->
            b.background = makeGravDrawable(idx+1, false)
            b.setTextColor(Color.parseColor("#6b6b67"))
        }
        btn.background = makeGravDrawable(g, true)
        btn.setTextColor(Color.parseColor(gravColors[g-1].first))
        selGrav = g
        checkForm()
    }

    // ── VALIDATION ──
    private fun checkForm() {
        val ok = selTypes.isNotEmpty() && selGrav > 0
        btnSave.isEnabled = ok
        tvHint.text = if (ok) "✓ Prêt à enregistrer" else "Sélectionnez au moins un type et une gravité"
    }

    // ── LISTENERS ──
    private fun setupListeners() {
        tabSaisie.setOnClickListener { showSaisie() }
        tabListe.setOnClickListener { showListe() }
        btnGps.setOnClickListener { requestGps() }
        photoZone.setOnClickListener { requestPhoto() }
        btnDelPhoto.setOnClickListener { clearPhoto() }
        btnSave.setOnClickListener { saveDesordre() }
        btnExport.setOnClickListener { doExport() }
    }

    // ── NAVIGATION ──
    private fun showSaisie() {
        pageSaisie.visibility = android.view.View.VISIBLE
        pageListe.visibility = android.view.View.GONE
        tabSaisie.setTextColor(Color.parseColor("#0f6e56"))
        tabListe.setTextColor(Color.parseColor("#6b6b67"))
        tabSaisie.setBackgroundResource(R.drawable.tab_selected)
        tabListe.setBackgroundResource(R.drawable.tab_normal)
    }

    private fun showListe() {
        pageSaisie.visibility = android.view.View.GONE
        pageListe.visibility = android.view.View.VISIBLE
        tabSaisie.setTextColor(Color.parseColor("#6b6b67"))
        tabListe.setTextColor(Color.parseColor("#0f6e56"))
        tabSaisie.setBackgroundResource(R.drawable.tab_normal)
        tabListe.setBackgroundResource(R.drawable.tab_selected)
        renderListe()
    }

    // ── GPS ──
    private fun requestGps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                PERM_LOC)
            return
        }
        doGps()
    }

    private fun doGps() {
        tvGpsResult.text = "⏳ Localisation en cours…"
        tvGpsResult.setTextColor(Color.parseColor("#6b6b67"))
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    gLat = "%.6f".format(loc.latitude)
                    gLon = "%.6f".format(loc.longitude)
                    val acc = loc.accuracy.toInt()
                    tvGpsResult.text = "✓ $gLat, $gLon  (±${acc}m)"
                    tvGpsResult.setTextColor(Color.parseColor("#0f6e56"))
                }
                override fun onProviderDisabled(p: String) {
                    tvGpsResult.text = "⚠ GPS désactivé — activez-le dans les réglages"
                    tvGpsResult.setTextColor(Color.parseColor("#a32d2d"))
                }
                override fun onProviderEnabled(p: String) {}
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
            }, null)
            // Fallback réseau si GPS lent
            lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    if (gLat.isEmpty()) {
                        gLat = "%.6f".format(loc.latitude)
                        gLon = "%.6f".format(loc.longitude)
                        val acc = loc.accuracy.toInt()
                        tvGpsResult.text = "✓ $gLat, $gLon  (±${acc}m, réseau)"
                        tvGpsResult.setTextColor(Color.parseColor("#0f6e56"))
                    }
                }
                override fun onProviderDisabled(p: String) {}
                override fun onProviderEnabled(p: String) {}
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
            }, null)
        } catch (e: Exception) {
            tvGpsResult.text = "⚠ Erreur GPS : ${e.message}"
            tvGpsResult.setTextColor(Color.parseColor("#a32d2d"))
        }
    }

    // ── PHOTO ──
    private fun requestPhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERM_CAM)
            return
        }
        launchCamera()
    }

    private fun launchCamera() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date())
        val photoFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "photo_$timeStamp.jpg")
        photoPath = photoFile.absolutePath
        currentPhotoUri = FileProvider.getUriForFile(this, "com.limours.voirie.fileprovider", photoFile)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
        }
        // Galerie aussi
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val chooser = Intent.createChooser(intent, "Photo").apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(galleryIntent))
        }
        startActivityForResult(chooser, REQ_PHOTO)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PHOTO && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: currentPhotoUri
            if (uri != null) {
                // Galerie
                if (data?.data != null) {
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date())
                    val destFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "photo_$timeStamp.jpg")
                    contentResolver.openInputStream(data.data!!)?.use { input ->
                        FileOutputStream(destFile).use { output -> input.copyTo(output) }
                    }
                    photoPath = destFile.absolutePath
                }
                showPhotoPreview(photoPath)
            }
        }
    }

    private fun showPhotoPreview(path: String) {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bmp = BitmapFactory.decodeFile(path, opts)
        if (bmp != null) {
            imgPreview.setImageBitmap(bmp)
            imgPreview.visibility = android.view.View.VISIBLE
            photoZone.visibility = android.view.View.GONE
            btnDelPhoto.visibility = android.view.View.VISIBLE
        }
    }

    private fun clearPhoto() {
        photoPath = ""
        imgPreview.visibility = android.view.View.GONE
        photoZone.visibility = android.view.View.VISIBLE
        btnDelPhoto.visibility = android.view.View.GONE
        imgPreview.setImageDrawable(null)
    }

    // ── SAUVEGARDE ──
    private fun saveDesordre() {
        if (selTypes.isEmpty() || selGrav == 0) {
            toast("Sélectionnez un type et une gravité")
            return
        }
        val types = selTypes.toMutableList().also {
            val idx = it.indexOf("Autre")
            if (idx >= 0 && etAutre.text.isNotBlank()) it[idx] = etAutre.text.toString().trim()
        }
        val now = Date()
        val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
        val heureFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)
        val photoNom = if (photoPath.isNotEmpty()) File(photoPath).name else ""

        val rec = JSONObject().apply {
            put("id", now.time)
            put("date", dateFmt.format(now))
            put("heure", heureFmt.format(now))
            put("lat", gLat)
            put("lon", gLon)
            put("rue", etRue.text.toString().trim())
            put("numero", etNum.text.toString().trim())
            put("types", JSONArray(types))
            put("gravite", selGrav)
            put("commentaire", etComment.text.toString().trim())
            put("photo_nom", photoNom)
            put("photo_path", photoPath)
        }

        val db = getDB()
        db.put(rec)
        saveDB(db)
        updateHeader()
        toast("✓ Désordre enregistré !")
        resetForm()
    }

    // ── RESET ──
    private fun resetForm() {
        gLat = ""; gLon = ""
        selTypes.clear()
        selGrav = 0
        photoPath = ""

        tvGpsResult.text = "Position non enregistrée"
        tvGpsResult.setTextColor(Color.parseColor("#6b6b67"))
        etRue.setText("")
        etNum.setText("")

        typeBtns.forEach {
            it.background = makeTypeDrawable(false)
            it.setTextColor(Color.parseColor("#1a1a18"))
        }
        etAutre.visibility = android.view.View.GONE
        etAutre.setText("")

        gravBtns.forEachIndexed { idx, b ->
            b.background = makeGravDrawable(idx+1, false)
            b.setTextColor(Color.parseColor("#6b6b67"))
        }

        clearPhoto()
        etComment.setText("")
        btnSave.isEnabled = false
        tvHint.text = "Sélectionnez au moins un type et une gravité"

        pageSaisie.scrollTo(0, 0)
    }

    // ── BASE DE DONNÉES (fichier JSON) ──
    private fun getDB(): JSONArray {
        val f = File(filesDir, "voirie_data.json")
        return if (f.exists()) try { JSONArray(f.readText()) } catch (e: Exception) { JSONArray() }
        else JSONArray()
    }

    private fun saveDB(db: JSONArray) {
        File(filesDir, "voirie_data.json").writeText(db.toString())
    }

    private fun updateHeader() {
        val n = getDB().length()
        tvCount.text = "$n désordre${if (n > 1) "s" else ""}"
    }

    // ── LISTE ──
    private fun renderListe() {
        val db = getDB()
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date())
        var graves = 0; var todays = 0
        for (i in 0 until db.length()) {
            val d = db.getJSONObject(i)
            if (d.optInt("gravite") >= 4) graves++
            if (d.optString("date") == today) todays++
        }
        tvTotal.text = db.length().toString()
        tvGraves.text = graves.toString()
        tvAujourd.text = todays.toString()

        listeContent.removeAllViews()
        if (db.length() == 0) {
            val tv = TextView(this).apply {
                text = "🛣 Aucun désordre enregistré"
                textSize = 15f
                setTextColor(Color.parseColor("#6b6b67"))
                gravity = Gravity.CENTER
                setPadding(0, 80, 0, 0)
            }
            listeContent.addView(tv)
            return
        }

        // Afficher du plus récent au plus ancien
        for (i in db.length()-1 downTo 0) {
            val d = db.getJSONObject(i)
            val view = LayoutInflater.from(this).inflate(R.layout.item_desordre, listeContent, false)

            val typesArr = d.optJSONArray("types")
            val typesStr = if (typesArr != null) {
                (0 until typesArr.length()).map { typesArr.getString(it) }.joinToString(" · ")
            } else d.optString("type", "—")

            val rue = d.optString("rue", "")
            val num = d.optString("numero", "")
            val addr = listOf(num, rue).filter { it.isNotEmpty() }.joinToString(" ")
                .ifEmpty { d.optString("lat","").let { if (it.isNotEmpty()) "$it, ${d.optString("lon","")}" else "Position non renseignée" } }

            view.findViewById<TextView>(R.id.tvItemTypes).text = typesStr
            view.findViewById<TextView>(R.id.tvItemAddr).text = addr
            view.findViewById<TextView>(R.id.tvItemDate).text = "${d.optString("date")} à ${d.optString("heure")}"

            val comment = d.optString("commentaire", "")
            val tvComment = view.findViewById<TextView>(R.id.tvItemComment)
            if (comment.isNotEmpty()) { tvComment.text = comment; tvComment.visibility = android.view.View.VISIBLE }
            else tvComment.visibility = android.view.View.GONE

            // Badge gravité
            val g = d.optInt("gravite", 1)
            val badge = view.findViewById<TextView>(R.id.tvItemGrav)
            badge.text = "$g"
            badge.setTextColor(Color.parseColor(gravColors[g-1].first))
            val badgeBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * resources.displayMetrics.density
                setColor(Color.parseColor(gravColors[g-1].second))
            }
            badge.background = badgeBg

            // Photo miniature
            val photoPath = d.optString("photo_path", "")
            val imgThumb = view.findViewById<ImageView>(R.id.imgThumb)
            if (photoPath.isNotEmpty() && File(photoPath).exists()) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                val bmp = BitmapFactory.decodeFile(photoPath, opts)
                if (bmp != null) imgThumb.setImageBitmap(bmp)
            }

            // Supprimer
            val id = d.optLong("id")
            view.findViewById<Button>(R.id.btnDelete).setOnClickListener {
                AlertDialog.Builder(this)
                    .setMessage("Supprimer ce désordre ?")
                    .setPositiveButton("Supprimer") { _, _ -> deleteItem(id) }
                    .setNegativeButton("Annuler", null)
                    .show()
            }

            listeContent.addView(view)
        }
    }

    private fun deleteItem(id: Long) {
        val db = getDB()
        val newDb = JSONArray()
        for (i in 0 until db.length()) {
            val d = db.getJSONObject(i)
            if (d.optLong("id") != id) newDb.put(d)
        }
        saveDB(newDb)
        updateHeader()
        renderListe()
        toast("Supprimé")
    }

    // ── EXPORT CSV ──
    private fun doExport() {
        val db = getDB()
        if (db.length() == 0) { toast("Aucun désordre à envoyer"); return }

        val cols = listOf("id","date","heure","lat","lon","rue","numero","types","gravite","commentaire","photo_nom")
        val sb = StringBuilder()
        sb.appendLine(cols.joinToString(","))

        for (i in 0 until db.length()) {
            val d = db.getJSONObject(i)
            val row = cols.map { c ->
                var v = when (c) {
                    "types" -> {
                        val arr = d.optJSONArray("types")
                        if (arr != null) (0 until arr.length()).map { arr.getString(it) }.joinToString(" | ")
                        else d.optString("type", "")
                    }
                    else -> d.opt(c)?.toString() ?: ""
                }
                v = v.replace("\"", "\"\"")
                if (v.contains(",") || v.contains("\n") || v.contains("\"")) v = "\"$v\""
                v
            }
            sb.appendLine(row.joinToString(","))
        }

        val today = SimpleDateFormat("dd-MM-yyyy", Locale.FRANCE).format(Date())
        val csvFile = File(filesDir, "voirie_limours_$today.csv")
        csvFile.writeText("\uFEFF$sb")  // BOM UTF-8 pour Excel

        val uri = FileProvider.getUriForFile(this, "com.limours.voirie.fileprovider", csvFile)

        val graves = (0 until db.length()).count { db.getJSONObject(it).optInt("gravite") >= 4 }
        val bodyText = "Tournée du $today\n${db.length()} désordre(s), $graves graves (G4-G5).\n\nFichier CSV en pièce jointe."

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Désordres voirie Limours — $today")
            putExtra(Intent.EXTRA_TEXT, bodyText)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Envoyer par…"))
    }

    // ── PERMISSIONS ──
    override fun onRequestPermissionsResult(reqCode: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(reqCode, perms, results)
        when (reqCode) {
            PERM_LOC -> if (results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) doGps()
                        else toast("Permission GPS refusée")
            PERM_CAM -> if (results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) launchCamera()
                        else toast("Permission caméra refusée")
        }
    }

    // ── TOAST ──
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
