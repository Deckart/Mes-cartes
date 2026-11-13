package com.example.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.text.Normalizer
import java.util.concurrent.TimeUnit

object StoreLogoFetcher {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val knownBrandDomains: List<Pair<List<String>, String>> = listOf(
        // Alimentation & grande distribution
        listOf("carrefour") to "carrefour.fr",
        listOf("e leclerc", "eleclerc", "leclerc") to "e.leclerc",
        listOf("auchan") to "auchan.fr",
        listOf("intermarche") to "intermarche.com",
        listOf("super u", "hyper u", "u express", "magasins u", "systeme u") to "magasins-u.com",
        listOf("monoprix") to "monoprix.fr",
        listOf("franprix") to "franprix.fr",
        listOf("picard") to "picard.fr",
        listOf("grand frais") to "grandfrais.com",
        listOf("biocoop") to "biocoop.fr",
        listOf("naturalia") to "naturalia.fr",
        listOf("la vie claire") to "lavieclaire.com",
        listOf("lidl") to "lidl.fr",
        listOf("aldi") to "aldi.fr",
        listOf("casino", "geant casino") to "casino.fr",
        listOf("cora") to "cora.fr",
        listOf("match", "supermarches match") to "supermarchesmatch.fr",
        listOf("thiriet") to "thiriet.com",
        listOf("nicolas") to "nicolas.com",
        listOf("nespresso") to "nespresso.com",
        listOf("starbucks") to "starbucks.fr",
        listOf("paul") to "paul.fr",
        listOf("mcdonald", "mcdo") to "mcdonalds.fr",
        listOf("burger king") to "burgerking.fr",

        // Maison, bricolage & jardin
        listOf("leroy merlin") to "leroymerlin.fr",
        listOf("castorama") to "castorama.fr",
        listOf("brico depot") to "bricodepot.fr",
        listOf("bricomarche") to "bricomarche.com",
        listOf("bricorama") to "bricorama.fr",
        listOf("mr bricolage", "monsieur bricolage") to "mr-bricolage.fr",
        listOf("weldom") to "weldom.fr",
        listOf("ikea") to "ikea.com",
        listOf("maisons du monde") to "maisonsdumonde.com",
        listOf("conforama") to "conforama.fr",
        listOf("but") to "but.fr",
        listOf("gifi") to "gifi.fr",
        listOf("centrakor") to "centrakor.com",
        listOf("action") to "action.com",
        listOf("normal") to "normal.eu",
        listOf("stokomani") to "stokomani.fr",
        listOf("hema") to "hema.com",
        listOf("jardiland") to "jardiland.com",
        listOf("truffaut") to "truffaut.com",
        listOf("botanic") to "botanic.com",
        listOf("gamm vert") to "gammvert.fr",
        listOf("animalis") to "animalis.com",
        listOf("maxi zoo") to "maxizoo.fr",

        // Beauté & santé
        listOf("sephora") to "sephora.fr",
        listOf("nocibe") to "nocibe.fr",
        listOf("marionnaud") to "marionnaud.fr",
        listOf("yves rocher") to "yves-rocher.fr",
        listOf("kiko") to "kikocosmetics.com",
        listOf("lush") to "lush.com",
        listOf("aroma zone", "aromazone") to "aroma-zone.com",
        listOf("adopt") to "adopt.com",
        listOf("rituals") to "rituals.com",
        listOf("the body shop") to "thebodyshop.com",
        listOf("loccitane", "l occitane") to "loccitane.com",
        listOf("april") to "april-beauty.com",

        // Culture, tech & loisirs
        listOf("fnac") to "fnac.com",
        listOf("cultura") to "cultura.com",
        listOf("micromania", "zing") to "micromania.fr",
        listOf("boulanger") to "boulanger.com",
        listOf("darty") to "darty.com",
        listOf("ldlc") to "ldlc.com",
        listOf("nature et decouvertes", "nature decouvertes") to "natureetdecouvertes.com",
        listOf("furet du nord") to "furet.com",
        listOf("gibert") to "gibert.com",
        listOf("pathe") to "pathe.fr",
        listOf("ugc") to "ugc.fr",
        listOf("cgr") to "cgrcinemas.fr",
        listOf("king jouet") to "king-jouet.com",
        listOf("joueclub") to "joueclub.fr",
        listOf("lego") to "lego.com",

        // Sport
        listOf("decathlon") to "decathlon.fr",
        listOf("intersport") to "intersport.fr",
        listOf("sport 2000", "sport2000") to "sport2000.fr",
        listOf("go sport") to "go-sport.com",
        listOf("courir") to "courir.com",
        listOf("jd sports") to "jdsports.fr",
        listOf("foot locker") to "footlocker.fr",
        listOf("ekosport") to "ekosport.fr",
        listOf("au vieux campeur") to "auvieuxcampeur.fr",

        // Mode & accessoires
        listOf("zara") to "zara.com",
        listOf("h&m", "hm") to "hm.com",
        listOf("kiabi") to "kiabi.com",
        listOf("celio") to "celio.com",
        listOf("jules") to "jules.com",
        listOf("etam") to "etam.com",
        listOf("promod") to "promod.fr",
        listOf("uniqlo") to "uniqlo.com",
        listOf("nike") to "nike.com",
        listOf("adidas") to "adidas.fr",
        listOf("primark") to "primark.com",
        listOf("gemo") to "gemo.fr",
        listOf("la halle") to "lahalle.com",
        listOf("chaussea") to "chaussea.com",
        listOf("besson") to "besson-chaussures.com",
        listOf("galeries lafayette") to "galerieslafayette.com",
        listOf("printemps") to "printemps.com",
        listOf("mango") to "mango.com",
        listOf("okaïdi", "okaidi") to "okaidi.fr",
        listOf("verbaudet", "vertbaudet") to "vertbaudet.fr",
        listOf("tape a loeil", "tao") to "t-a-o.com",
        listOf("armand thiery") to "armandthiery.fr",
        listOf("cache cache") to "cache-cache.fr",
        listOf("bonobo") to "bonoboplanet.com",

        // Auto & mobilité
        listOf("norauto") to "norauto.fr",
        listOf("feu vert") to "feuvert.fr",
        listOf("totalenergies", "total") to "totalenergies.fr",
        listOf("sncf") to "sncf-connect.com"
    )

    internal fun normalizeText(raw: String): String {
        val decomposed = Normalizer.normalize(raw.trim().lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace("'", " ")
            .replace("’", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    internal fun sanitizeDomainInput(raw: String): String? {
        val cleaned = raw.trim()
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore("/")
            .substringBefore("?")
            .trim()
        return cleaned.takeIf { it.contains('.') && it.length >= 4 && !it.contains(' ') }
    }

    fun resolveCandidateDomains(storeName: String, customDomain: String? = null): List<String> {
        val candidates = linkedSetOf<String>()

        customDomain?.let(::sanitizeDomainInput)?.let { candidates.add(it) }

        // Si l'utilisateur a saisi directement un nom de domaine comme nom d'enseigne
        sanitizeDomainInput(storeName)?.let { candidates.add(it) }

        val normalized = normalizeText(storeName)
        if (normalized.isNotBlank()) {
            // Recherche exacte puis correspondance partielle par mots-clés connus
            knownBrandDomains.forEach { (keywords, domain) ->
                if (keywords.any { kw ->
                        normalized == kw ||
                            normalized.contains(" $kw") ||
                            normalized.contains("$kw ") ||
                            (kw.length >= 4 && normalized.contains(kw))
                    }
                ) {
                    candidates.add(domain)
                }
            }

            // Heuristique de domaine si aucune enseigne connue n'a été trouvée (ou en complément)
            val compactSlug = normalized.replace(Regex("[^a-z0-9]"), "")
            val hyphenSlug = normalized.replace(Regex("[^a-z0-9]+"), "-").trim('-')
            if (compactSlug.length >= 3) {
                candidates.add("$compactSlug.fr")
                candidates.add("$compactSlug.com")
            }
            if (hyphenSlug.length >= 3 && hyphenSlug != compactSlug) {
                candidates.add("$hyphenSlug.fr")
                candidates.add("$hyphenSlug.com")
            }
        }

        return candidates.toList()
    }

    suspend fun fetchLogoPngBytes(storeName: String, customDomain: String? = null): ByteArray? =
        withContext(Dispatchers.IO) {
            val domains = resolveCandidateDomains(storeName, customDomain)
            for (domain in domains) {
                val hostExists = runCatching {
                    java.net.InetAddress.getByName(domain)
                    true
                }.getOrDefault(false)
                if (!hostExists) continue

                val urls = listOf(
                    "https://www.$domain/apple-touch-icon.png",
                    "https://$domain/apple-touch-icon.png",
                    "https://www.$domain/apple-touch-icon-precomposed.png",
                    "https://t2.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://$domain&size=256",
                    "https://icons.duckduckgo.com/ip3/$domain.ico"
                )
                for (url in urls) {
                    val bitmap = downloadValidBitmap(url)
                    if (bitmap != null) {
                        val pngBytes = encodeLogoPng(bitmap)
                        bitmap.recycle()
                        if (pngBytes != null) {
                            return@withContext pngBytes
                        }
                    }
                }
            }
            null
        }

    private fun downloadValidBitmap(url: String): Bitmap? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            if (contentType.isNotEmpty() &&
                !contentType.contains("image") &&
                !contentType.contains("octet-stream")
            ) {
                return@runCatching null
            }
            val bytes = response.body?.bytes() ?: return@runCatching null
            if (bytes.isEmpty() || bytes.size > 2_000_000) return@runCatching null
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
            // Rejeter les minuscules icônes génériques 16x16 / 32x32 par défaut
            if (decoded.width < 48 || decoded.height < 48) {
                decoded.recycle()
                return@runCatching null
            }
            decoded
        }
    }.getOrNull()

    fun encodeLogoPng(source: Bitmap, maxDimension: Int = 320): ByteArray? = runCatching {
        val w = source.width
        val h = source.height
        val scaled = if (w > maxDimension || h > maxDimension) {
            val ratio = minOf(maxDimension.toFloat() / w, maxDimension.toFloat() / h)
            val newW = (w * ratio).toInt().coerceAtLeast(1)
            val newH = (h * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(source, newW, newH, true)
        } else {
            source
        }
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, baos)
        if (scaled !== source) scaled.recycle()
        baos.toByteArray()
    }.getOrNull()
}
