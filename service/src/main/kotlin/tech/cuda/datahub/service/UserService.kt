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

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTDecodeException
import me.liuwj.ktorm.dsl.*
import me.liuwj.ktorm.entity.add
import tech.cuda.datahub.service.dao.UserDAO
import tech.cuda.datahub.service.dto.UserDTO
import tech.cuda.datahub.service.dto.toUserDTO
import tech.cuda.datahub.service.exception.DuplicateException
import tech.cuda.datahub.service.exception.NotFoundException
import tech.cuda.datahub.service.po.UserPO
import tech.cuda.datahub.service.utils.Encoder
import java.time.LocalDateTime
import java.util.*

/**
 * @author Jensen Qi <jinxiu.qi@alu.hit.edu.cn>
 * @since 1.0.0
 */
object UserService : Service(UserDAO) {

    private const val EXPIRE_TIME = 86400000L // 默认 token 失效时间为 1 天

    /**
     * 检查提供的 [username] 和 [password] 是否与数据库中的匹配, 常用于第一次登录
     * 如果匹配，则生成为期 1 天的 token
     * 如果不匹配或生成失败，则返回 null
     */
    fun sign(username: String, password: String): String? {
        val user = find<UserPO>(where = UserDAO.name eq username) ?: return null
        if (user.password != Encoder.md5(password)) return null
        return try {
            JWT.create().withClaim("username", user.name)
                .withExpiresAt(Date(System.currentTimeMillis() + EXPIRE_TIME))
                .sign(Algorithm.HMAC256(user.password))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从[token]中解析出 username，如果解析失败，则返回 null
     */
    private fun getUsername(token: String) = try {
        JWT.decode(token).getClaim("username").asString()
    } catch (e: JWTDecodeException) {
        null
    }

    /**
     * 通过 token 获取用户信息
     */
    fun getUserByToken(token: String): UserDTO? {
        if (!verify(token)) return null
        val username = getUsername(token) ?: return null
        return findByName(username)
    }

    /**
     * 判断 token 是否为数据库中某一条记录生成的
     * 如果匹配，则返回 true 否则返回 false
     */
    fun verify(token: String): Boolean {
        val username = getUsername(token) ?: return false
        val user = UserDAO.select(UserDAO.password)
            .where { UserDAO.isRemove eq false and (UserDAO.name eq username) }
            .map { UserDAO.createEntity(it) }
            .firstOrNull() ?: return false
        return try {
            JWT.require(Algorithm.HMAC256(user.password)).withClaim("username", username).build().verify(token)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 分页查询用户列表，支持模糊搜索
     */
    fun listing(page: Int, pageSize: Int, pattern: String? = null): Pair<List<UserDTO>, Int> {
        val (users, count) = batch<UserPO>(
            page,
            pageSize,
            exclude = UserDAO.password,
            filter = UserDAO.isRemove eq false,
            like = UserDAO.name.match(pattern),
            orderBy = UserDAO.id.asc()
        )
        return users.map { it.toUserDTO() } to count
    }

    /**
     *
     */
    fun findByName(name: String) = find<UserPO>(
        where = (UserDAO.isRemove eq false) and (UserDAO.name eq name),
        exclude = UserDAO.password
    )?.toUserDTO()

    fun findById(id: Int) = find<UserPO>(
        where = (UserDAO.isRemove eq false) and (UserDAO.id eq id),
        exclude = UserDAO.password
    )?.toUserDTO()

    fun create(name: String, password: String, groups: Set<Int>, email: String): UserDTO {
        findByName(name)?.let { throw DuplicateException("用户 $name 已存在") }
        val user = UserPO {
            this.name = name
            this.groups = groups
            this.password = Encoder.md5(password)
            this.email = email
            this.isRemove = false
            this.createTime = LocalDateTime.now()
            this.updateTime = LocalDateTime.now()
        }
        UserDAO.add(user)
        return user.toUserDTO()
    }

    fun update(id: Int, name: String? = null, password: String? = null, groups: Set<Int>? = null, email: String? = null): UserDTO {
        val user = find<UserPO>(
            where = (UserDAO.isRemove eq false) and (UserDAO.id eq id),
            exclude = UserDAO.password
        ) ?: throw NotFoundException("用户 $id 不存在或已被删除")
        name?.let {
            findByName(name)?.let { throw DuplicateException("用户 $name 已存在") }
            user.name = name
        }
        password?.let { user.password = Encoder.md5(password) }
        groups?.let { user.groups = groups }
        email?.let { user.email = email }
        anyNotNull(name, password, groups, email)?.let {
            user.updateTime = LocalDateTime.now()
            user.flushChanges()
        }
        return user.toUserDTO()
    }

    fun remove(id: Int) {
        val user = find<UserPO>(
            where = (UserDAO.isRemove eq false) and (UserDAO.id eq id),
            exclude = UserDAO.password
        ) ?: throw NotFoundException("用户 $id 不存在或已被删除")
        user.isRemove = true
        user.updateTime = LocalDateTime.now()
        user.flushChanges()
    }
}

