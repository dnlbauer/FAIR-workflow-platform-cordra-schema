package de.senckenberg.cwr.types

import de.senckenberg.cwr.Validator
import net.cnri.cordra.CordraType
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraException
import net.cnri.cordra.api.CordraObject

@CordraType("Person")
class PersonType: JsonLdType(listOf("Person"), coercedTypes = listOf("affiliation")) {

    override fun beforeSchemaValidation(co: CordraObject, context: HooksContext): CordraObject {
        val person = co.content.asJsonObject

        if (!Validator.validateIdentifier(person)) {
            throw CordraException.fromStatusCode(400, "Identifier is not a valid URI identifier.")
        }

        return super.beforeSchemaValidation(co, context)
    }

}
