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
package tech.cuda.datahub.service.i18n

/**
 * @author Jensen Qi <jinxiu.qi@alu.hit.edu.cn>
 * @since 1.0.0
 */
data class Chinese(
    override val user: String = "用户",
    override val group: String = "项目组",
    override val notExistsOrHasBeenRemove: String = "不存在或已被删除",
    override val machine: String = "调度服务器",
    override val file: String = "文件节点",
    override val fileMirror: String = "文件镜像",
    override val task: String = "调度任务",
    override val job: String = "调度作业",
    override val instance: String = "调度实例",
    override val operationNotAllow: String = "非法操作"
) : Language
