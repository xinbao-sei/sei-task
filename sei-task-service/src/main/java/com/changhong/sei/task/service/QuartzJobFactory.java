package com.changhong.sei.task.service;

import com.changhong.sei.apitemplate.ApiTemplate;
import com.changhong.sei.core.context.ContextUtil;
import com.changhong.sei.core.context.mock.MockUser;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.log.LogUtil;
import com.changhong.sei.core.util.JsonUtils;
import com.changhong.sei.task.dao.JobHistoryDao;
import com.changhong.sei.task.entity.JobHistory;
import com.changhong.sei.task.service.manager.ErrorNotifyManager;
import com.changhong.sei.util.DateUtils;
import com.changhong.sei.util.thread.ThreadLocalHolder;
import com.changhong.sei.util.thread.ThreadLocalUtil;
import com.changhong.sei.utils.AsyncRunUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Map;
import java.util.Objects;

/**
 * *************************************************************************************************
 * <p/>
 * 实现功能：定时任务运行工厂
 * <p>
 * ------------------------------------------------------------------------------------------------
 * 版本          变更时间             变更人                     变更原因
 * ------------------------------------------------------------------------------------------------
 * 1.0.00      2017-08-03 10:12      王锦光(wangj)                新建
 * <p/>
 * *************************************************************************************************
 */
@Component
@DisallowConcurrentExecution
public class QuartzJobFactory extends QuartzJobBean {
    @Autowired
    private MockUser mockUser;
    @Autowired
    private ApiTemplate apiTemplate;
    @Autowired
    private AsyncRunUtil asyncRunUtil;
    @Autowired
    private ErrorNotifyManager errorNotifyManager;
    /**
     * 调度任务的键值
     */
    public static final String SCHEDULER_KEY = "scheduleJob";

    /**
     * 获取默认租户代码
     *
     * @return 租户代码
     */
    public static String getTenantCode() {
        return ContextUtil.getProperty("sei.default-tenant.code");
    }

    /**
     * 获取默认租户管理员
     *
     * @return 租户管理员
     */
    public static String getTenantAdmin() {
        return ContextUtil.getProperty("sei.default-tenant.admin");
    }

    /**
     * 设置执行任务的用户
     */
    public void setToTenantAdmin(String tenantCode, String account) {
        if (StringUtils.isNotBlank(tenantCode) && StringUtils.isNotBlank(account)) {
            mockUser.mockUser(tenantCode, account);
            return;
        }
        if (StringUtils.isNotBlank(getTenantCode()) && StringUtils.isNotBlank(getTenantAdmin())) {
            mockUser.mockUser(getTenantCode(), getTenantAdmin());
        }
    }

    /**
     * Execute the actual job. The job data map will already have been
     * applied as bean property values by execute. The contract is
     * exactly the same as for the standard Quartz execute method.
     *
     * @param context 执行的任务上下文
     * @see #execute
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    protected void executeInternal(JobExecutionContext context) {
        com.changhong.sei.task.entity.Job scheduleJob = (com.changhong.sei.task.entity.Job) context.getMergedJobDataMap().get(SCHEDULER_KEY);
        if (Objects.isNull(scheduleJob)) {
            return;
        }
        JobHistory history = new JobHistory();
        com.changhong.sei.task.entity.Job job = new com.changhong.sei.task.entity.Job();
        job.setId(scheduleJob.getId());
        history.setJob(job);
        Date date = DateUtils.getCurrentDateTime();
        history.setStartTime(date);
        //定义执行时间记录
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        LogUtil.bizLog("{} 任务开始执行 start", scheduleJob.getName());
        try {
            // 初始化线程变量
            ThreadLocalHolder.begin();
            String path = String.format("%s/%s", scheduleJob.getApiPath(), scheduleJob.getMethodName());
            // 反序列化得到输入参数
            Map params = null;
            String inputParam = scheduleJob.getInputParam();
            if (!StringUtils.isBlank(inputParam)) {
                params = JsonUtils.fromJson(inputParam, Map.class);
            }
            // 确定任务执行的输入参数
            final Map inputParams = params;
            // 设置任务执行用户
            setToTenantAdmin(scheduleJob.getExeTenantCode(), scheduleJob.getExeAccount());
            // 判断是否为异步执行
            ResultData result;
            if (scheduleJob.getAsyncExe()) {
                asyncRunUtil.runAsync(() -> {
                    apiTemplate.postByAppModuleCode(scheduleJob.getAppModuleCode(), path, ResultData.class, inputParams);
                });
                result = ResultData.success("任务【" + scheduleJob.getName() + "】已提交后台异步执行。", null);
            } else {
                result = apiTemplate.postByAppModuleCode(scheduleJob.getAppModuleCode(), path, ResultData.class, inputParams);
            }
            stopWatch.stop();
            LogUtil.bizLog("{} 任务执行完成 end", scheduleJob.getName());
            history.setSuccessful(result.successful());
            history.setMessage(result.getMessage());
            // 发送通知邮件
            if (result.failed()) {
                errorNotifyManager.sendEmail(scheduleJob, result.getMessage(), null);
            }
        } catch (Exception e) {
            String msg = String.format("执行[%s]异常！jobId:%s", scheduleJob.getName(), scheduleJob.getId());
            LogUtil.error(msg + "；Token信息:" + ThreadLocalUtil.getTranVar(ContextUtil.HEADER_TOKEN_KEY), e);
            stopWatch.stop();
            history.setSuccessful(false);
            history.setMessage("作业执行失败！");
            history.setExceptionMessage(msg);
            // 发送通知邮件
            errorNotifyManager.sendEmail(scheduleJob, msg, e);
        } finally {
            history.setElapsed(stopWatch.getTime());
            try {
                JobHistoryDao jobHistoryDao = ContextUtil.getBean(JobHistoryDao.class);
                jobHistoryDao.save(history);
            } catch (Exception e) {
                String msg = String.format("保存作业执行历史异常：%s", JsonUtils.toJson(history));
                LogUtil.error(msg, e);
            }
            // 释放线程变量
            ThreadLocalHolder.end();
        }
    }
}
