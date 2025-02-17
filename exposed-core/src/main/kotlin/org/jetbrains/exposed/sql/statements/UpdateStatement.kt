package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.H2Dialect.H2CompatibilityMode
import org.jetbrains.exposed.sql.vendors.H2FunctionProvider
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.h2Mode

/**
 * Represents the SQL statement that updates rows of a table.
 *
 * @param targetsSet Column set to update rows from. This may be a [Table] or a [Join] instance.
 * @param limit Maximum number of rows to update.
 * @param where Condition that determines which rows to update.
 */
open class UpdateStatement(val targetsSet: ColumnSet, val limit: Int?, val where: Op<Boolean>? = null) :
    UpdateBuilder<Int>(StatementType.UPDATE, targetsSet.targetTables()) {

    /** The initial list of columns to update with their updated values. */
    open val firstDataSet: List<Pair<Column<*>, Any?>> get() = values.toList()

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): Int {
        if (values.isEmpty()) return 0
        return executeUpdate()
    }

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        require(firstDataSet.isNotEmpty()) { "Can't prepare UPDATE statement without fields to update" }

        val dialect = transaction.db.dialect
        return when (targetsSet) {
            is Table -> dialect.functionProvider.update(targetsSet, firstDataSet, limit, where, transaction)
            is Join -> {
                val functionProvider = when (dialect.h2Mode) {
                    H2CompatibilityMode.PostgreSQL, H2CompatibilityMode.Oracle, H2CompatibilityMode.SQLServer -> H2FunctionProvider
                    else -> dialect.functionProvider
                }
                functionProvider.update(targetsSet, firstDataSet, limit, where, transaction)
            }
            else -> transaction.throwUnsupportedException("UPDATE with ${targetsSet::class.simpleName} unsupported")
        }
    }

    override fun arguments(): Iterable<Iterable<Pair<IColumnType, Any?>>> = QueryBuilder(true).run {
        when {
            targetsSet is Join && currentDialect is OracleDialect -> {
                where?.toQueryBuilder(this)
                values.forEach {
                    registerArgument(it.key, it.value)
                }
            }
            else -> {
                values.forEach {
                    registerArgument(it.key, it.value)
                }
                where?.toQueryBuilder(this)
            }
        }
        if (args.isNotEmpty()) listOf(args) else emptyList()
    }
}
