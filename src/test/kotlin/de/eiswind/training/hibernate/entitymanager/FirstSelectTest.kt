package de.eiswind.training.hibernate.entitymanager


import de.eiswind.training.hibernate.domain.Customer
import de.eiswind.training.hibernate.util.PersistenceHelper.properties
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FirstSelectTest {

    private lateinit var em: EntityManager

    @BeforeEach
    fun setup() {
        em = emf.createEntityManager()
    }

    @AfterEach
    fun tearDown() {
        // never ever forget to close it
        if (em.isOpen()) {
            em.close()
        }
    }

    @Test
    fun firstSelect() {
        val c = em.find<Customer?>(Customer::class.java, 33L)
        c shouldNotBe null
        c!!.firstname shouldBe "Buck"
        c.lastname shouldBe "Rogers"
    }


    companion object {

        private lateinit var emf: EntityManagerFactory

        @BeforeAll
        @JvmStatic
        fun setupEmf() {
            // this is expensive and we run it only once at startup
            emf = Persistence.createEntityManagerFactory("default", properties())
        }
    }
}
