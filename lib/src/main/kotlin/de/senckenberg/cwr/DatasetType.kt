/*
 * This source file was generated by the Gradle 'init' task
 */
package de.senckenberg.cwr

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.cnri.cordra.CordraHooksSupportProvider
import net.cnri.cordra.CordraMethod
import net.cnri.cordra.CordraType
import net.cnri.cordra.CordraTypeInterface
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraException
import net.cnri.cordra.api.CordraObject
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.logging.Logger

@CordraType("Dataset")
class DatasetType : CordraTypeInterface {

    override fun beforeSchemaValidation(co: CordraObject, ctx: HooksContext): CordraObject {
        val json = co.content.asJsonObject

        if (json.has("about") && json.get("about").isJsonObject) {
            if (!Validator.validateIdentifier(json.get("about").asJsonObject)) {
                throw CordraException.fromStatusCode(400, "Taxon identifier is not a valid URI identifier.")
            }
        }

        return co
    }

    @CordraMethod("toCrate", allowGet = true)
    fun toROCrate(obj: CordraObject, ctx: HooksContext): JsonElement {
        // TODO parse RO Crate
        return Gson().toJsonTree(Unit)
    }

    companion object {
        val cordra = CordraHooksSupportProvider.get().cordraClient
        val logger = Logger.getLogger(this::class.simpleName)

        private fun addAuthor(author: JsonElement): String = cordra.create("Person", author).id

        private fun <T> processIfExists(obj: JsonObject, key: String, processingFunction: (JsonElement) -> T): Set<T> {
            if (!obj.has(key)) {
                return emptySet()
            }

            val elements = obj.get(key)
            return if (elements.isJsonArray) {
                elements.asJsonArray.map(processingFunction).toSet()
            } else {
                setOf(processingFunction(elements))
            }
        }

        fun JsonArray.findElementWithId(id: String): JsonObject? =
            this.firstOrNull { it.asJsonObject.get("@id").asString == id }?.asJsonObject

        @Throws(CordraException::class)
        @CordraMethod("fromROCrate")
        @JvmStatic
        fun fromROCrate(ctx: HooksContext): JsonElement {
            val cordra = CordraHooksSupportProvider.get().cordraClient

            val json = ctx.params.asJsonObject!!
            logger.info { "Processing $json." }

            // find dataset element
            val graph = json.getAsJsonArray("@graph")
                ?: throw CordraException.fromStatusCode(400, "Missing @graph array element.")
            val metadataEntity = graph.findElementWithId("ro-crate-metadata.json")
                ?: throw CordraException.fromStatusCode(400, "Missing dataset element.")
            val datasetId = metadataEntity.getAsJsonObject("about").get("@id").asString
            val datasetEntity = graph.findElementWithId(datasetId)
                ?: throw CordraException.fromStatusCode(400, "Missing dataset element.")

            // TODO validate about identifier and/or Taxon

            // TODO insert author into cordra

            // TODO insert parts + payloads into cordra
            // TODO insert mentions into cordra

            // process dataset entity to cordra object
            val dataset = JsonObject()
            applyTypeAndContext(dataset, "Dataset", "https://schema.org")

            for (key in datasetEntity.keySet()) {
                when (key) {
                    // exclude jsonld special attributes
                    "@id", "@type", "@context", "conformsTo" -> continue
                    // resolve file entities to media objects with payload
                    "hasPart" -> continue // TODO add media objecs
                    "about" -> continue // TODO parse about
                    // resolve author entities to Person objects
                    "author" -> {
                        val authorIds = if (datasetEntity.get(key).isJsonObject) {
                            listOf(datasetEntity.getAsJsonObject(key).get("@id").asString)
                        } else {
                            datasetEntity.getAsJsonArray(key).map { it.asJsonObject.get("@id").asString }
                        }
                        val authorCordraIds = mutableListOf<String>()
                        for (id in authorIds) {
                            val elem = graph.findElementWithId(id) ?: continue
                            // preserve author id as identifier if it's a valid uri
                            if (Validator.isUri(id)) {
                                elem.addProperty("identifier", id)
                            }
                            val obj = cordra.create("Person", elem)
                            authorCordraIds.add(obj.id)
                        }
                        dataset.add("author", JsonArray().apply { authorCordraIds.forEach { add(it) } })
                    }
                    "mentions" -> continue // TODO add create actions
                    // try to add everything else as string primitive
                    else -> {
                        val elem = datasetEntity.get(key)
                        if (elem.isJsonPrimitive) {
                            dataset.addProperty(key, elem.asJsonPrimitive.asString)
                        }
                    }
                }
            }

            // add dateCreated, dateModified, datePublished
            val nowStr = LocalDateTime.now().let {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
                formatter.format(it)
            }
            for (property in arrayOf("dateCreated", "dateModified", "datePublished")) {
                if (!dataset.has(property)) {
                    dataset.addProperty(property, nowStr)
                }
            }

            val co = cordra.create("Dataset", dataset)
            return co.content
        }
    }

}
