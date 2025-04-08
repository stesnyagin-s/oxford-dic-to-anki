import edu.stanford.nlp.simple.Sentence
import kotlinx.html.BODY
import kotlinx.html.a
import kotlinx.html.b
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.html
import kotlinx.html.i
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.ul
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

private const val WORD_STYLE = "color:MediumBlue; font-size: 28px; font-weight: 600;text-decoration:none;margin-right: 10px"

fun main() {
    val defNumber = 1
    val url = "https://www.oxfordlearnersdictionaries.com/search/english/?q=detachment"
    val doc: Document = Jsoup.connect(url).get()
    val elements = doc.select("div.webtop > span.phonetics").single().children().map {
        Phonetic(
            country = it.attribute("geo")!!.value, phonetic = it.getElementsByClass("phon").text()
        )
    }
    val selectedWord = doc.select(".webtop > .headword").single().text()
    val word = Word(
        word = selectedWord,
        defNumber = defNumber,
        partOfSpeech = doc.select(".webtop > .pos").single().text(),
        phonetics = elements,
        labels = doc.select(".webtop > .labels").text().takeIf { it.isNotBlank() },
        senses = buildSenses(doc = doc, selectedWord = selectedWord),
        url = url
    )
    val frontHtml = getFrontHtml(word)

    val hiddenFrontHtml = getHiddenFrontHtml(word)

    val backHtml = getBackHtml(word = word, defNumber = defNumber, url = url)
    println(frontHtml)
    println(backHtml)
    println(hiddenFrontHtml)
}

private fun getFrontHtml(word: Word) = createHTML().html {
    body {
        span {
            attributes["style"] = WORD_STYLE
            text(word.word)
        }
        span {
            attributes["style"] = "font-style: italic;color:grey;"
            text(" ${word.partOfSpeech}")
        }
        ul {
            attributes["style"] = "margin: 0"
            selectChosenDef(word).example.forEach {
                li {
                    it.cf?.let { cf -> b { text("$cf ") } }
                    span {
                        attributes["style"] = "font-style: italic;margin-top: 5px"
                        text(it.text)
                    }
                }
            }
        }
    }
}

private fun getBackHtml(word: Word, defNumber: Int, url: String) = createHTML().html {
    body {
        a {
            attributes["style"] = WORD_STYLE
            href = url
            span {
                text(word.word)
            }
        }
        span {
            attributes["style"] = "font-style: italic;color:grey;"
            text(" ${word.partOfSpeech}")
        }
        ul {
            attributes["style"] = "list-style-type:none;padding-left: 0;margin-top: 15px;margin-bottom: 20px"
            word.phonetics.forEach {
                li {
                    div {
                        attributes["style"]="display: grid; grid-template-columns: 1fr 2fr;"
                        span {
                            attributes["style"]="font-weight: 600"
                            text(it.country)
                        }
                        span {
                            text(" ${it.phonetic}")
                        }

                    }
                }
            }
        }
        word.labels?.also { labels ->
            p {
                attributes["style"] = "color:grey;"
                text(labels)
            }
        }
        span {
            attributes["style"]="font-weight: 600"
            text("$defNumber. ${selectChosenDef(word).cf?.plus(" ") ?: ""}")
            span {
                attributes["style"] = "color:grey;"
                text(selectChosenDef(word).labels?.plus(" ") ?: "")
            }
        }

        text(selectChosenDef(word).def)
        addSynonym(word)
        ul {
            attributes["style"] = "margin: 0"
            selectChosenDef(word).example.forEach {
                li {
                    it.cf?.let { cf -> b { text("$cf ") } }
                    span {
                        attributes["style"] = "font-style: italic;margin-top: 5px"
                        text(it.text)
                    }
                }
            }
        }
    }
}

private fun BODY.addSynonym(word: Word) {
    selectChosenDef(word).synonym?.let {
        p {
            attributes["style"] = "color:grey;"
            text(it)
        }
    }
}

private fun getHiddenFrontHtml(word: Word) = createHTML().html {
    body {
        span {
            attributes["style"] = "font-style: italic;"
            text(word.partOfSpeech)
        }
        p {
            text(selectChosenDef(word).hiddenDef)
        }

        addSynonym(word = word)
        ul {
            selectChosenDef(word).example.forEach {
                li {
                    i{text(it.hiddenText)}
                }
            }
        }
    }
}

fun selectChosenDef(word: Word): Sense {
    return word.senses[word.defNumber - 1]
}

private fun buildSenses(doc: Document, selectedWord: String) =
    doc.select("ol.senses_multiple > li.sense, ol.sense_single > li.sense").map { sense ->
        val grammar = sense.select(".grammar")
        val synonyms = sense.select(".xrefs[xt=\"syn\"]")
        val cfs = sense.select("> .cf")
        val labels = sense.select(".labels")
        check(synonyms.size in 0..1)
        check(grammar.size in 0..1)
        check(cfs.size in 0..1)
        check(labels.size in 0..1)

        val def = sense.select(".def").single().text()
        Sense(grammar = grammar.singleOrNull()?.text(),
            cf = cfs.singleOrNull()?.text(),
            def = def,
            labels = labels.singleOrNull()?.text(),
            synonym = synonyms.singleOrNull()?.text(),
            hiddenDef = hideWord(word = selectedWord, sentence = def),
            example = sense.select("> ul.examples > li").map { example ->
                val cf = example.select("> .cf")
                check(cf.size in 0..1)
                val text = example.select("> .x").single().text()
                Example(
                    cf = cf.singleOrNull()?.text(),
                    text = text,
                    hiddenText = hideWord(word = selectedWord, sentence = text),
                )
            })
    }

fun hideWord(word: String, sentence: String): String {
    val wordLemma = Sentence(word).lemma(0)
    var hiddenSentence = sentence
    val wordsToFilter = Sentence(sentence).words().filter { Sentence(it).lemma(0) == wordLemma }
        .forEach { hiddenSentence = hiddenSentence.replace(it, "____") }
    return hiddenSentence
}

@Serializable
data class Word(
    val word: String,
    val defNumber: Int,
    val partOfSpeech: String,
    val labels: String?,
    val phonetics: List<Phonetic>,
    val senses: List<Sense>,
    val url: String,
)

@Serializable
data class Phonetic(
    val country: String,
    val phonetic: String,
)

@Serializable
data class Sense(
    val cf: String?,
    val def: String,
    val hiddenDef: String,
    val grammar: String?,
    val labels: String?,
    val example: List<Example>,
    val synonym: String?
)

@Serializable
data class Example(val cf: String?, val text: String, val hiddenText: String)