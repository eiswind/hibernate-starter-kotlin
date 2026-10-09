package de.eiswind.training.hibernate.util

import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence
import net.ttddyy.dsproxy.listener.ChainListener
import net.ttddyy.dsproxy.listener.DataSourceQueryCountListener
import net.ttddyy.dsproxy.listener.logging.DefaultQueryLogEntryCreator
import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel
import net.ttddyy.dsproxy.listener.logging.SLF4JQueryLoggingListener
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder
import org.h2.jdbcx.JdbcDataSource
import org.hibernate.cfg.AvailableSettings
import org.hibernate.engine.jdbc.internal.FormatStyle
import org.hibernate.engine.jdbc.internal.Formatter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import java.util.concurrent.Callable
import java.util.function.Consumer
import java.util.function.Function
import javax.sql.DataSource
import kotlin.concurrent.Volatile



object PersistenceHelper {

    @JvmStatic
    val log: Logger = LoggerFactory.getLogger(PersistenceHelper::class.java)

    var enableLogging: Boolean = true

    var enableCache: Boolean = false

    private fun dataSource(): JdbcDataSource {
        val prop = loadConfiguration()
        val ds = JdbcDataSource()
        ds.setUser("sa")
        ds.setUrl(prop.getProperty("jdbc.url"))
        return ds
    }

    private fun loadConfiguration(): Properties {
        val prop = Properties()
        try {
            prop.load(
                PersistenceHelper::class.java.getClassLoader()
                    .getResourceAsStream("config/persistence.properties")
            )
        } catch (e: IOException) {
            log.error("Failed to load properties", e)
        }
        return prop
    }

    private fun proxyDataSource(): DataSource? {
        val ds = dataSource()
        if (!enableLogging) {
            return ds
        }
        val creator = PrettyQueryEntryCreator
        creator.setMultiline(true)

        val chainListener = ChainListener()
        val queryLoggingListener =
            SLF4JQueryLoggingListener()
        queryLoggingListener.setQueryLogEntryCreator(creator)
        chainListener.addListener(queryLoggingListener)
        chainListener.addListener(DataSourceQueryCountListener())
        queryLoggingListener.setLogLevel(SLF4JLogLevel.INFO)
        return ProxyDataSourceBuilder
            .create(ds)
            .name("proxy")
            .listener(chainListener)
            .build()
    }

    fun properties(): MutableMap<String?, Any?> {
        val properties: MutableMap<String?, Any?> = HashMap<String?, Any?>()
        properties.put(AvailableSettings.JAKARTA_NON_JTA_DATASOURCE, proxyDataSource())
        properties.put(AvailableSettings.HBM2DDL_AUTO, "validate")
        properties.put("hibernate.query.mutation_strategy.global_temporary.create_tables", false)
        properties.put("hibernate.query.mutation_strategy.global_temporary.drop_tables", false)
        properties.put(
            AvailableSettings.QUERY_MULTI_TABLE_MUTATION_STRATEGY,
            "org.hibernate.query.sqm.mutation.internal.inline.InlineMutationStrategy"
        )
        properties.put(AvailableSettings.PREFERRED_INSTANT_JDBC_TYPE, "TIMESTAMP")


        properties.put(AvailableSettings.STATEMENT_BATCH_SIZE, 50)

        if (enableCache) {
            properties.put(
                AvailableSettings.CACHE_REGION_FACTORY,
                "jcache"
            )
        }
        return properties
    }

    // tag::emf[]
    @Volatile
    private var emf: EntityManagerFactory? = null

    val entityManagerFactory: EntityManagerFactory
        get() {
            if (emf == null) {
                synchronized(PersistenceHelper::class.java) {
                    if (emf == null) {
                        emf = Persistence.createEntityManagerFactory(
                            "default", properties()
                        )
                    }
                }
            }
            return emf!!
        }


    val entityManager: EntityManager
        get() = entityManagerFactory.createEntityManager()


    fun <T> callInHibernate(function: Function<EntityManager, T>): T {
        val em: EntityManager = entityManager
        try {
            return function.apply(em)
        } finally {
            if (em.isOpen()) {
                em.close()
            }
        }
    }


    fun doInHibernate(consumer: Consumer<EntityManager>) {
        val em: EntityManager = entityManager
        try {
            consumer.accept(em)
        } finally {
            if (em.isOpen()) {
                em.close()
            }
        }
    }


    fun withTransaction(em: EntityManager, runnable: Runnable) {
        val transaction = em.getTransaction()
        try {
            transaction.begin()
            runnable.run()
            if (transaction.isActive()) {
                transaction.commit()
            }
        } catch (x: RuntimeException) {
            x.printStackTrace()
            if (transaction.isActive()) {
                transaction.rollback()
            }
            throw x
        }
    }


    fun <V> callInTransaction(em: EntityManager, callable: Callable<V>): V {
        val transaction = em.getTransaction()
        try {
            transaction.begin()
            return callable.call()
        } catch (x: Exception) {
            if (transaction.isActive()) {
                transaction.rollback()
            }
            throw RuntimeException("Error in TX", x)
        } finally {
            if (transaction.isActive()) {
                transaction.commit()
            }
        }
    }



}

private object PrettyQueryEntryCreator : DefaultQueryLogEntryCreator() {
    private val formatter: Formatter = FormatStyle.BASIC.getFormatter()

    override fun formatQuery(query: String?): String? {
        return this.formatter.format(query)
    }
}
