package app.chrapak.ui

/** Polish names for the sounds YAMNet reports most often in a bedroom; any other label stays as the model gives it. */
private val POLISH = mapOf(
    "Silence" to "Cisza",
    "Breathing" to "Oddychanie",
    "Snoring" to "Chrapanie",
    "Speech" to "Mowa",
    "Conversation" to "Rozmowa",
    "Narration, monologue" to "Monolog",
    "Snort" to "Parsknięcie",
    "Wheeze" to "Świst oddechu",
    "Gasp" to "Westchnienie",
    "Cough" to "Kaszel",
    "Throat clearing" to "Chrząkanie",
    "Sneeze" to "Kichnięcie",
    "Sniff" to "Pociąganie nosem",
    "Inside, small room" to "Małe pomieszczenie",
    "Animal" to "Zwierzę",
    "Insect" to "Owad",
    "Cricket" to "Świerszcz",
    "Music" to "Muzyka",
    "Television" to "Telewizja",
    "Radio" to "Radio",
    "Typing" to "Pisanie",
    "Computer keyboard" to "Klawiatura",
    "Walk, footsteps" to "Kroki",
    "Door" to "Drzwi",
    "Telephone" to "Telefon",
    "Vacuum cleaner" to "Odkurzacz",
    "Water tap, faucet" to "Kran",
    "Hands" to "Dłonie",
    "Clapping" to "Klaskanie",
    "Fart" to "Odgłos jelit",
)

fun classNamePl(label: String): String = POLISH[label] ?: label
