package com.lc.activiti.web;

import com.lc.activiti.model.FormVariables;
import com.lc.activiti.model.TaskDynamicFormModel;
import com.lc.activiti.model.TaskModel;
import org.activiti.engine.FormService;
import org.activiti.engine.TaskService;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.form.TaskFormData;
import org.activiti.engine.task.Task;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务控制class.
 *
 * @author zyz.
 */
@RestController
@RequestMapping("/task")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private FormService formService;

    @GetMapping("/list")
    public Object todoTask(@RequestParam("userId") String userId) {

        List<Task> taskList = taskService.createTaskQuery().taskAssignee(userId).list();

        List<Task> waitingClaimTasks = taskService.createTaskQuery().taskCandidateUser(userId).list();

        List<Task> allTasks = new ArrayList<>();
        allTasks.addAll(taskList);
        allTasks.addAll(waitingClaimTasks);

        List<TaskModel> resTaskList = new ArrayList<>();
        for (Task task : allTasks) {
            TaskModel model = new TaskModel();
            BeanUtils.copyProperties(task, model);
            resTaskList.add(model);
        }

        return resTaskList;
    }

    /**
     * 任务签收.
     *
     * @param taskId
     * @return
     */
    @GetMapping("/claim")
    public ResponseEntity claim(@RequestParam("id") String taskId,
                                @RequestParam("userId") String userId) {

        taskService.claim(taskId, userId);

        return ResponseEntity.ok().build();
    }

    /**
     * 任务表单.
     *
     * @param taskId
     * @return
     */
    @GetMapping("/taskform")
    public ResponseEntity<Map<String,Object>> readTaskForm(@RequestParam("taskId") String taskId) {
        TaskFormData taskFormData = formService.getTaskFormData(taskId);

        Map<String,Object> content = new HashMap<>();
        Map<String,Object> formMap = new HashMap<>();
        if (StringUtils.isNotEmpty(taskFormData.getFormKey())) {
            Object renderedTaskForm = formService.getRenderedTaskForm(taskId);
            Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
            content.put("task", task);
            content.put("taskFormdata", renderedTaskForm);
            content.put("hasFormKey", true);
        } else {

            List<TaskDynamicFormModel> modelList = new ArrayList<>();

            List<FormProperty> formProperties = taskFormData.getFormProperties();

            for (FormProperty formProperty : formProperties) {
                TaskDynamicFormModel model = new TaskDynamicFormModel();
                model.setId(formProperty.getId());
                model.setName(formProperty.getName());
                model.setTypeName(formProperty.getType().getName());
                model.setValue(formProperty.getValue());

                // 该表单是否可编辑。
                model.setWritable(formProperty.isWritable());
                modelList.add(model);

                // 表单值传递。
                formMap.put(formProperty.getId(), formProperty.getValue());
            }

            content.put("taskId", taskId);
            content.put("taskFormdata", modelList);
            content.put("formMap", formMap);

        }

        return new ResponseEntity<>(content, HttpStatus.OK);
    }

    @PostMapping("/complete")
    public ResponseEntity completeTask(@RequestBody FormVariables formVariables) {

        TaskFormData taskFormData = formService.getTaskFormData(formVariables.getTaskId());

        List<FormProperty> formProperties = taskFormData.getFormProperties();

        Map<String, String> formValues = new HashMap<>();
        for (FormProperty formProperty : formProperties) {
            if (formProperty.isWritable()) {
                formValues.put(formProperty.getId(), formVariables.getFormPropertiesData().get(formProperty.getId()));
            }
        }

        // 提交用户任务表单并完成任务。
        formService.submitTaskFormData(formVariables.getTaskId(), formValues);

        return ResponseEntity.ok().build();
    }


}
