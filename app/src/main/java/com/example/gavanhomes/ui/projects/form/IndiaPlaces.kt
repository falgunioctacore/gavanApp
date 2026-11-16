package com.example.gavanhomes.ui.projects.form

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** States and union territories, and the main cities / districts of each — the State → City pickers of the project form. */
object IndiaPlaces {
    val states = listOf(
        "Andaman and Nicobar Islands", "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chandigarh", "Chhattisgarh",
        "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand",
        "Karnataka", "Kerala", "Ladakh", "Lakshadweep", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha",
        "Puducherry", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal",
    )

    private val cities: Map<String, List<String>> = mapOf(
        "Gujarat" to listOf(
            "Ahmedabad", "Amreli", "Anand", "Aravalli (Modasa)", "Banaskantha (Palanpur)", "Bharuch", "Bhavnagar", "Botad", "Chhota Udepur", "Dahod", "Dang (Ahwa)",
            "Devbhumi Dwarka (Khambhalia)", "Gandhinagar", "Gir Somnath (Veraval)", "Jamnagar", "Junagadh", "Kheda (Nadiad)", "Kutch (Bhuj)", "Mahisagar (Lunawada)",
            "Mehsana", "Morbi", "Narmada (Rajpipla)", "Navsari", "Panchmahal (Godhra)", "Patan", "Porbandar", "Rajkot", "Sabarkantha (Himmatnagar)", "Surat",
            "Surendranagar", "Tapi (Vyara)", "Vadodara", "Valsad", "Vapi", "Ankleshwar", "Bilimora", "Gandhidham", "Mundra", "Sanand", "Dholera", "Kalol", "Mamkwada",
            "Umbergaon", "Pardi", "Dharampur", "Silvassa",
        ),
        "Maharashtra" to listOf("Mumbai", "Thane", "Navi Mumbai", "Pune", "Nashik", "Nagpur", "Aurangabad (Chhatrapati Sambhajinagar)", "Kolhapur", "Solapur", "Palghar", "Vasai-Virar", "Bhiwandi", "Raigad (Alibag)", "Satara", "Sangli", "Ahmednagar", "Jalgaon", "Amravati"),
        "Rajasthan" to listOf("Jaipur", "Jodhpur", "Udaipur", "Kota", "Ajmer", "Bikaner", "Bhilwara", "Alwar", "Sikar", "Pali", "Sri Ganganagar", "Chittorgarh", "Banswara"),
        "Madhya Pradesh" to listOf("Bhopal", "Indore", "Gwalior", "Jabalpur", "Ujjain", "Sagar", "Dewas", "Ratlam", "Rewa", "Satna"),
        "Delhi" to listOf("New Delhi", "Delhi"),
        "Karnataka" to listOf("Bengaluru", "Mysuru", "Mangaluru", "Hubballi-Dharwad", "Belagavi", "Kalaburagi", "Ballari", "Davanagere", "Shivamogga", "Tumakuru"),
        "Tamil Nadu" to listOf("Chennai", "Coimbatore", "Madurai", "Tiruchirappalli", "Salem", "Tiruppur", "Erode", "Vellore", "Tirunelveli", "Hosur"),
        "Uttar Pradesh" to listOf("Lucknow", "Kanpur", "Noida", "Ghaziabad", "Agra", "Varanasi", "Prayagraj", "Meerut", "Bareilly", "Aligarh", "Greater Noida", "Gorakhpur"),
        "West Bengal" to listOf("Kolkata", "Howrah", "Durgapur", "Asansol", "Siliguri", "Bardhaman"),
        "Telangana" to listOf("Hyderabad", "Warangal", "Nizamabad", "Karimnagar", "Khammam", "Secunderabad"),
        "Andhra Pradesh" to listOf("Visakhapatnam", "Vijayawada", "Guntur", "Tirupati", "Nellore", "Kurnool", "Rajahmundry", "Amaravati"),
        "Kerala" to listOf("Thiruvananthapuram", "Kochi", "Kozhikode", "Thrissur", "Kollam", "Kannur", "Palakkad"),
        "Punjab" to listOf("Ludhiana", "Amritsar", "Jalandhar", "Patiala", "Mohali", "Bathinda"),
        "Haryana" to listOf("Gurugram", "Faridabad", "Panipat", "Ambala", "Karnal", "Hisar", "Rohtak", "Sonipat"),
        "Bihar" to listOf("Patna", "Gaya", "Muzaffarpur", "Bhagalpur", "Darbhanga"),
        "Odisha" to listOf("Bhubaneswar", "Cuttack", "Rourkela", "Berhampur", "Sambalpur"),
        "Chhattisgarh" to listOf("Raipur", "Bhilai", "Bilaspur", "Korba", "Durg"),
        "Jharkhand" to listOf("Ranchi", "Jamshedpur", "Dhanbad", "Bokaro"),
        "Uttarakhand" to listOf("Dehradun", "Haridwar", "Haldwani", "Roorkee"),
        "Himachal Pradesh" to listOf("Shimla", "Solan", "Mandi", "Kangra"),
        "Goa" to listOf("Panaji", "Margao", "Vasco da Gama", "Mapusa"),
        "Assam" to listOf("Guwahati", "Dibrugarh", "Silchar", "Jorhat"),
        "Jammu and Kashmir" to listOf("Srinagar", "Jammu"),
        "Chandigarh" to listOf("Chandigarh"),
        "Puducherry" to listOf("Puducherry"),
        "Dadra and Nagar Haveli and Daman and Diu" to listOf("Daman", "Diu", "Silvassa"),
    )

    fun citiesOf(state: String): List<String> = cities[state].orEmpty()

    /** "Banaskantha (Palanpur)" → "Palanpur" is what the post office knows it by; plain names stay as they are. */
    private fun postalName(city: String) = city.substringAfter('(', "").substringBefore(')').ifBlank { city }.trim()

    /** State and city (district) of a 6-digit pincode, from the India Post lookup. Blocking — call on an IO thread. Null when not found / offline. */
    fun place(pincode: String): Pair<String, String>? {
        val conn = URL("https://api.postalpincode.in/pincode/" + pincode).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            val o = JSONArray(conn.inputStream.bufferedReader().readText()).optJSONObject(0)?.optJSONArray("PostOffice")?.optJSONObject(0) ?: return null
            val raw = o.optString("State")
            val state = states.firstOrNull { it.equals(raw, true) } ?: states.firstOrNull { it.replace(" and ", " & ").equals(raw.replace(" and ", " & "), true) } ?: raw
            state to o.optString("District").ifBlank { o.optString("Name") }
        } catch (e: Exception) { android.util.Log.w("IndiaPlaces", "pincode lookup failed: " + e); null } finally { conn.disconnect() }
    }

    /** One pincode of a city: the code and the post office / area it belongs to. */
    class Pin(val code: String, val area: String)

    /**
     * Pincodes of [city] (in [state] when given) from the India Post lookup. Blocking — call on an IO thread. An empty list means
     * nothing was found (or no network): the field stays a normal text box, so it can always be typed.
     */
    fun pincodes(city: String, state: String?): List<Pin> {
        val name = postalName(city).ifBlank { return emptyList() }
        val conn = URL("https://api.postalpincode.in/postoffice/" + URLEncoder.encode(name, "UTF-8").replace("+", "%20")).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            val body = conn.inputStream.bufferedReader().readText()
            val offices = JSONArray(body).optJSONObject(0)?.optJSONArray("PostOffice") ?: return emptyList()
            val all = (0 until offices.length()).map { offices.getJSONObject(it) }
            val inState = all.filter { state.isNullOrBlank() || it.optString("State").equals(state, true) || state.startsWith(it.optString("State"), true) }
            (inState.ifEmpty { all }).map { Pin(it.optString("Pincode"), it.optString("Name")) }.filter { it.code.length == 6 }.distinctBy { it.code }.sortedBy { it.code }
        } catch (e: Exception) { emptyList() } finally { conn.disconnect() }
    }
}
