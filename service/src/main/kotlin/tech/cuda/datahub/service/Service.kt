/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tech.cuda.datahub.service

import me.liuwj.ktorm.dsl.*
import me.liuwj.ktorm.entity.Entity
import me.liuwj.ktorm.expression.BinaryExpression
import me.liuwj.ktorm.expression.OrderByExpression
import me.liuwj.ktorm.schema.Column
import me.liuwj.ktorm.schema.Table
import kotlin.math.max

/**
 * @author Jensen Qi <jinxiu.qi@alu.hit.edu.cn>
 * @since 1.0.0
 */
abstract class Service(private val table: Table<*>) {

    /**
     * 生成字符串匹配过滤条件, 其中, [pattern] 是以空格(可连续)间隔的一个或多个字符串, NULL 字符串会被过滤掉
     */
    protected fun Column<String>.match(pattern: String?): BinaryExpression<Boolean>? {
        if (pattern.isNullOrBlank()) return null
        val filters = pattern.trim().split("\\s+".toRegex())
            .filter { it.toUpperCase() != "NULL" }
            .map { this.like("%$it%") }
        if (filters.isEmpty()) return null
        return filters.reduce { a, b -> a and b }
    }

    protected fun anyNotNull(vararg args: Any?): Any? {
        for (arg in args) {
            if (arg != null) {
                return arg
            }
        }
        return null
    }

    /**
     * 计算页面大小为[pageSize]时，[pageId]的页面偏移量
     * 其中，[pageId] 以 1 开始计算（即，第一页是 [pageId] = 1 而不是 [pageId] = 0)
     */
    private fun offset(pageId: Int, pageSize: Int) = max(pageSize * (pageId - 1), 0)

    private operator fun List<Column<*>>.minus(col: Column<*>?): List<Column<*>> {
        return if (col == null) {
            this
        } else {
            this.filter { it.name != col.name }
        }
    }

    private operator fun BinaryExpression<Boolean>?.plus(another: BinaryExpression<Boolean>?): BinaryExpression<Boolean>? {
        return when {
            this == null && another != null -> another
            this != null && another == null -> this
            this != null && another != null -> this and another
            else -> null
        }
    }

    /**
     * 批量查询页面大小为[pageSize]时第[pageId]页的数据
     * 如果提供了[exclude]，则排出掉改列，比如，你可能不希望返回用户的密码列
     * 如果提供了[filter]，则过滤出[filter]为 true 的记录
     * 如果提供了[like]，则过滤出[filter] and [like] 为 true 的记录
     * 如果提供了[orderBy]，则对结果进行排序后再阶段返回
     * 最终返回类型为[T]，大小为[pageSize]的数组(如果不足则可能小于[pageSize])
     * 以及符合[filter]的总记录总数
     */
    protected fun <T : Entity<*>> batch(
        pageId: Int,
        pageSize: Int,
        exclude: Column<*>? = null,
        filter: BinaryExpression<Boolean>? = null,
        like: BinaryExpression<Boolean>? = null,
        orderBy: OrderByExpression? = null
    ): Pair<List<T>, Int> {
        var items = table.select(table.columns - exclude)
        val filters = filter + like
        filters?.let { items = items.where { filters } }
        val count = items.totalRecords
        orderBy?.let { items = items.orderBy(orderBy) }
        return items.limit(offset(pageId, pageSize), pageSize).map { table.createEntity(it) as T } to count
    }

    protected fun <T : Entity<*>> find(where: BinaryExpression<Boolean>, exclude: Column<*>? = null): T? {
        return table.select(table.columns - exclude)
            .where { where }
            .map { table.createEntity(it) as T }
            .firstOrNull()
    }

}
