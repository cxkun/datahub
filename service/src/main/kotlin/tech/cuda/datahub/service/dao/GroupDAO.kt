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
package tech.cuda.datahub.service.dao

import tech.cuda.datahub.service.po.GroupPO
import me.liuwj.ktorm.schema.*
import tech.cuda.datahub.annotation.mysql.*

/**
 * @author Jensen Qi <jinxiu.qi@alu.hit.edu.cn>
 * @since 1.0.0
 */
@STORE_IN_MYSQL
internal object GroupDAO : Table<GroupPO>("groups") {

    @BIGINT
    @UNSIGNED
    @AUTO_INCREMENT
    @PRIMARY_KEY
    @NOT_NULL
    @COMMENT("项目组 ID")
    val id by int("id").primaryKey().bindTo { it.id }

    @VARCHAR(64)
    @COMMENT("项目组名称")
    val name by varchar("name").bindTo { it.name }

    @BOOL
    @NOT_NULL
    @COMMENT("逻辑删除")
    val isRemove by boolean("is_remove").bindTo { it.isRemove }

    @DATETIME
    @NOT_NULL
    @COMMENT("创建时间")
    val createTime by datetime("create_time").bindTo { it.createTime }

    @DATETIME
    @NOT_NULL
    @COMMENT("更新时间")
    val updateTime by datetime("update_time").bindTo { it.updateTime }
}

