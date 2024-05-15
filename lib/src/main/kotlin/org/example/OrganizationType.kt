package org.example

import net.cnri.cordra.CordraType
import net.cnri.cordra.CordraTypeInterface
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraObject

@CordraType("Organization")
class OrganizationType: CordraTypeInterface {

    override fun beforeSchemaValidation(co: CordraObject, context: HooksContext): CordraObject {
        val org = co.content.asJsonObject
        org.applyTypeAndContext("Organization", "https://schema.org/Organization")

        return co
    }

}