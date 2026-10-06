package md.vicproj.app

/** Aceleasi numere si mesaje de eroare ca in AppError.cs (aplicatia Windows). */
object AppError {
    const val NETWORK = 1
    const val SERVER = 2
    const val CAMERA_MODULE = 3
    const val DOWNLOAD = 4
    const val CANNOT_OPEN_LINK = 5
    const val FILE_SYSTEM = 6
    const val UNEXPECTED = 9

    fun title(code: Int): String = when (code) {
        NETWORK -> "Fără conexiune"
        SERVER -> "Serverul nu răspunde"
        CAMERA_MODULE -> "Camera nu poate porni"
        DOWNLOAD -> "Descărcarea a eșuat"
        CANNOT_OPEN_LINK -> "Nu am putut deschide linkul"
        FILE_SYSTEM -> "Eroare de fișier"
        else -> "Eroare neașteptată"
    }

    fun message(code: Int): String = when (code) {
        NETWORK -> "Verifică-ți conexiunea la internet și încearcă din nou."
        SERVER -> "S-ar putea să fie o problemă temporară pe server. Mai încearcă o dată în câteva clipe."
        CAMERA_MODULE -> "Modulul de scanare QR nu a putut porni. Poți introduce codul manual, cu tastatura, în locul scanării."
        DOWNLOAD -> "Fișierul nu a putut fi descărcat complet. Încearcă din nou."
        CANNOT_OPEN_LINK -> "Linkul nu a putut fi deschis."
        FILE_SYSTEM -> "Nu am putut citi sau scrie un fișier necesar."
        else -> "Ceva nu a mers cum trebuia. Dacă se repetă, notează numărul erorii de mai jos."
    }

    fun format(code: Int, detail: String? = null): String {
        val text = "Eroare #$code — ${title(code)}. ${message(code)}"
        return if (detail == null) text else "$text ($detail)"
    }
}

/** Starea unei ferestre de eroare afisate peste ecranul curent. */
data class ErrorInfo(val code: Int, val detail: String? = null)

/** Mesaj simplu (echivalentul MessageBox din Windows). */
data class MessageInfo(val title: String, val message: String)
