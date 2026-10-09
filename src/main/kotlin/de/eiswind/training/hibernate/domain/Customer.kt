package de.eiswind.training.hibernate.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
class Customer {

    @Id
    var id: Long? = null

    var firstname: String = ""

    var lastname: String = ""
}

