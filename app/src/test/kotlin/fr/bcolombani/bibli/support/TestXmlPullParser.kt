package fr.bcolombani.bibli.support

import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser

/**
 * Le `android.jar` utilisé par les tests unitaires ne fournit que des stubs pour
 * `XmlPullParserFactory`. Les tests injectent donc une implémentation réelle (kXML 2),
 * qui est justement celle qu'Android embarque.
 */
fun testXmlPullParser(): XmlPullParser = KXmlParser().apply {
    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
}
