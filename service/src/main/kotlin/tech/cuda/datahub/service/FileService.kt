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

import me.liuwj.ktorm.database.Database
import me.liuwj.ktorm.dsl.*
import me.liuwj.ktorm.entity.add
import tech.cuda.datahub.service.dao.FileDAO
import tech.cuda.datahub.service.dto.*
import tech.cuda.datahub.service.dto.toFileDTO
import tech.cuda.datahub.service.exception.DuplicateException
import tech.cuda.datahub.service.exception.NotFoundException
import tech.cuda.datahub.service.exception.PermissionException
import tech.cuda.datahub.service.po.FilePO
import tech.cuda.datahub.service.po.dtype.FileType
import java.lang.IllegalArgumentException
import java.time.LocalDateTime

/**
 * @author Jensen Qi <jinxiu.qi@alu.hit.edu.cn>
 * @since 1.0.0
 */
object FileService : Service(FileDAO) {

    /**
     * 判断一个文件节点是否为根节点（只有根节点的 parentID 允许为 null)
     */
    private fun FilePO.isRootDir() = this.parentId == null

    /**
     * 将 FileDTO 按文件类型和文件名排序
     */
    private fun List<FileDTO>.sort() = this.sortedWith(
        compareBy(
            //文件类型作为第一优先级
            {
                when (it.type) {
                    FileType.DIR -> 0
                    FileType.SQL -> 1
                    FileType.MR -> 2
                    FileType.SPARK -> 3
                }
            },
            // 文件名作为第二优先级
            {
                it.name
            }
        )
    )

    /**
     * 模糊查询，如果不提供合法搜索词则直接返回空
     */
    fun search(pattern: String): Pair<List<FileDTO>, Int> {
        val files = FileDAO.select(FileDAO.columns.filter { it != FileDAO.content }).where {
            val match = FileDAO.name.match(pattern)
            if (match != null) {
                FileDAO.isRemove eq false and match
            } else { // 非法搜索词
                return listOf<FileDTO>() to 0
            }
        }
        val count = files.totalRecords
        return files.map { FileDAO.createEntity(it).toFileDTO() }.sort() to count
    }

    /**
     * 获取给定文件节点[id]的直属子节点（即不会递归地返回孙节点）
     */
    fun listChildren(id: Int): Pair<List<FileDTO>, Int> {
        val files = FileDAO.select(FileDAO.columns.filter { it != FileDAO.content }).where {
            FileDAO.isRemove eq false and (FileDAO.parentId eq id)
        }
        val count = files.totalRecords
        return files.map { FileDAO.createEntity(it).toFileDTO() }.sort() to count
    }


    /**
     * 获取给定文件节点[id]的父路径
     * 如果给定的文件节点不存在或已被删除，则抛出 NotFoundException
     * 如果文件节点的任何一个父节点不存在或已被删除，则抛出 NotFoundException
     * 如果文件节点的任何一个父节点类型不是文件夹，则抛出 IllegalArgumentException
     */
    fun listParent(id: Int): Pair<List<FileDTO>, Int> {
        var currentFile = find<FilePO>(where = FileDAO.isRemove eq false and (FileDAO.id eq id))
            ?: throw NotFoundException("文件节点 $id 不存在或已被删除")
        val parentList = mutableListOf<FilePO>()
        while (!currentFile.isRootDir()) { // kotlin 的类型推断有点问题，这里需要强制声明 currentFile 非 NULL
            val parent = find<FilePO>(where = FileDAO.isRemove eq false and (FileDAO.id eq currentFile.parentId!!))  // 非根节点一定有 parent ID
                ?: throw NotFoundException("文件节点 ${currentFile.id} 父节点不存在或已被删除")
            when {
                parent.type != FileType.DIR -> throw IllegalArgumentException("父节点必须是文件夹类型")
                else -> {
                    parentList.add(0, parent)
                    currentFile = parent
                }
            }
        }
        return parentList.map { it.toFileDTO() } to parentList.size
    }

    /**
     * 获取指定[id]的文件节点
     * 如果不存在或已被删除则返回 null
     */
    fun findById(id: Int) = find<FilePO>(FileDAO.isRemove eq false and (FileDAO.id eq id))?.toFileDTO()

    /**
     * 查找给定项目组[groupId]的文件夹根目录
     * 如果不存在或已被删除，则抛出 NotFoundException
     */
    fun findRootByGroupId(groupId: Int): FileDTO {
        val root = find<FilePO>(
            where = (FileDAO.isRemove eq false) and (FileDAO.groupId eq groupId) and (FileDAO.parentId.isNull())
        ) ?: throw NotFoundException("项目组 $groupId 根目录不存在或已被删除")
        return root.toFileDTO()
    }


    /**
     * 获取给定文件节点[id]的文件内容
     * 如果给定的节点[id]不存在或已被删除，则抛出 NotFoundException
     * 如果给定的节点是文件夹类型，则抛出 IllegalArgumentException
     */
    fun getContent(id: Int): FileContentDTO {
        val file = find<FilePO>(FileDAO.isRemove eq false and (FileDAO.id eq id))
            ?: throw NotFoundException("文件节点 $id 不存在或已被删除")
        if (file.type == FileType.DIR) throw IllegalArgumentException("文件夹无法获取 content")
        return file.toFileContentDTO()
    }

    /**
     * 创建项目组的根目录
     */
    internal fun createRoot(groupId: Int, ownerId: Int): FilePO {
        val file = FilePO {
            this.groupId = groupId
            this.ownerId = ownerId
            this.name = "解决方案"
            this.type = FileType.DIR
            this.content = null
            this.parentId = null
            this.isRemove = false
            this.createTime = LocalDateTime.now()
            this.updateTime = LocalDateTime.now()
        }
        FileDAO.add(file)
        return file
    }


    /**
     * 创建文件节点
     * 如果父节点[parentId]不存在或已被删除，则抛出 NotFoundException
     * 如果父节点[parentId]不是文件夹，则抛出 IllegalArgumentException
     * 如果父节点[parentId]下已存在类型为[type]的同名[name]文件，则抛出 DuplicateException
     * 如果项目组[groupId]不存在或已被删除，则抛出 NotFoundException
     * 如果用户[user]不存在或已被删除，则抛出 NotFoundException
     * 如果用户[user]不归属[groupId]项目组，则抛出 PermissionException
     */
    fun create(groupId: Int, user: UserDTO, name: String, type: FileType, parentId: Int): FileDTO {
        val parent = findById(parentId) ?: throw NotFoundException("父节点 $parentId 不存在或已被删除")
        if (parent.type != FileType.DIR) throw IllegalArgumentException("父节点 $parentId 不是文件夹")
        GroupService.findById(groupId) ?: throw NotFoundException("项目组 $groupId 不存在或已被删除")
        UserService.findById(user.id) ?: throw NotFoundException("用户 ${user.id} 不存在或已被删除")
        if (!user.groups.contains(groupId)) throw PermissionException("用户 ${user.id} 不是项目组 $groupId 用户")
        find<FilePO>(
            where = FileDAO.isRemove eq false
                and (FileDAO.parentId eq parentId)
                and (FileDAO.name eq name)
                and (FileDAO.type eq type)
        )?.let { throw DuplicateException("文件夹 $parentId 下已存在类型为 $type 的节点 $name") }
        val file = FilePO {
            this.groupId = groupId
            this.ownerId = ownerId
            this.name = name
            this.type = type
            this.content = type.initVal(user.name, LocalDateTime.now())
            this.parentId = parentId
            this.isRemove = false
            this.createTime = LocalDateTime.now()
            this.updateTime = LocalDateTime.now()
        }
        FileDAO.add(file)
        return file.toFileDTO()
    }

    /**
     * 更新指定[id]的文件属性
     * 如果指定的文件节点不存在或已被删除，则抛出 NotFoundException
     * 如果指定的文件节点是项目组的根节点，则抛出 IllegalArgumentException
     * 如果试图更新[ownerId]，且该用户不存在或已被删除，则抛出 NotFoundException
     * 如果试图更新[ownerId]，且该用户不拥有该文件所在项目空间的权限，则抛出 PermissionException
     * 如果试图更新[name]，且父节点下已经存在同名&同类型的节点，则抛出 DuplicateException
     * 如果试图更新[content]，且给定的节点是文件夹，则抛出 IllegalArgumentException
     * 如果试图更新[parentId]，且父节点不存在或已删除，则抛出 NotFoundException
     * 如果试图更新[parentId]，且父节点不是文件夹，则抛出 IllegalArgumentException
     * 如果试图更新[parentId]，且父节点与当前文件归属不同的项目，则抛出 PermissionException
     */
    fun update(id: Int, ownerId: Int? = null, name: String? = null, content: String? = null, parentId: Int? = null): FileDTO {
        val file = find<FilePO>(FileDAO.isRemove eq false and (FileDAO.id eq id))
            ?: throw NotFoundException("文件节点 $id 不存在或已被删除")
        if (file.isRootDir()) throw IllegalArgumentException("根节点不允许更改")
        ownerId?.let {
            val user = UserService.findById(ownerId) ?: throw NotFoundException("用户 $ownerId 不存在或已被删除")
            if (!user.groups.contains(file.groupId)) throw PermissionException("用户 $ownerId 不是项目组 ${file.groupId} 用户")
            file.ownerId = ownerId
        }
        name?.let {
            find<FilePO>(
                where = FileDAO.isRemove eq false
                    and (FileDAO.parentId eq file.parentId!!) // 前面已经检查过非根节点，因此 parentID 一定不为 null
                    and (FileDAO.name eq name)
                    and (FileDAO.type eq file.type)
            )?.apply { throw DuplicateException("文件夹 ${file.parentId} 下已存在类型为 ${file.type} 的节点 $name") }
            file.name = name
        }
        content?.let {
            if (file.type == FileType.DIR) throw IllegalArgumentException("文件夹禁止更新 content 属性")
            file.content = content
        }
        parentId?.let {
            val parent = findById(parentId) ?: throw NotFoundException("父节点 $parentId 不存在或已被删除")
            if (parent.type != FileType.DIR) throw IllegalArgumentException("父节点 $parentId 不是文件夹")
            if (parent.groupId != file.groupId) throw PermissionException("父节点与当前节点的项目组不同")
            file.parentId = parentId
        }
        anyNotNull(ownerId, name, content, parentId)?.let {
            file.updateTime = LocalDateTime.now()
            file.flushChanges()
        }
        return file.toFileDTO()
    }

    /**
     * 递归地逻辑删除节点[parentId]的子节点
     */
    private fun removeChildRecursive(parentId: Int) {
        Database.global.update(FileDAO) {
            it.isRemove to true
            it.updateTime to LocalDateTime.now()
            where { it.parentId eq parentId and (it.isRemove eq false) }
        }
        FileDAO.select().where { FileDAO.type eq FileType.DIR and (FileDAO.parentId eq parentId) }.forEach {
            removeChildRecursive(FileDAO.createEntity(it).id)
        }
    }

    /**
     * 删除文件指定[id]的文件节点
     * 如果该节点的类型是文件夹，则递归地删除它下面的所有节点
     * 如果该节点不存在或已删除，则抛出 NotFoundException
     * 如果该节点是根节点，则抛出 IllegalArgumentException
     */
    fun remove(id: Int) {
        val file = find<FilePO>(FileDAO.isRemove eq false and (FileDAO.id eq id))
            ?: throw NotFoundException("文件节点 $id 不存在或已被删除")
        if (file.isRootDir()) throw IllegalArgumentException("根节点不允许删除")
        file.isRemove = true
        file.updateTime = LocalDateTime.now()
        if (file.type == FileType.DIR) {
            removeChildRecursive(file.id)
        }
        file.flushChanges()
    }
}

