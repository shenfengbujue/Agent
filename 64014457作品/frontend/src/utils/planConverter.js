/**
 * AI学习方案 → 个性化学习格式转换器
 *
 * 输入: planData JSON (6维结构化数据)
 * 输出: StudyGoal.resources 格式 (version=2, 阶段+每日任务)
 */

export function convertPlanToStudyFormat(planData) {
  const stages = planData?.learningPath?.stages || [];
  const allModules = planData?.knowledge || [];
  const allExercises = planData?.exercises?.exercises || [];
  const allReadings = planData?.webSearch?.resources || [];
  const totalDaysAll = stages.reduce((sum, s) => sum + (s.days || 1), 0);

  const convertedStages = stages.map((stage, si) => {
    const stageDays = stage.days || 1;
    const stageModules = matchModules(stage.modules || [], allModules);
    const stageExercises = distributeItems(allExercises, si, stages.length, stageDays, totalDaysAll);
    const stageReadings = distributeItems(allReadings, si, stages.length, stageDays, totalDaysAll);

    const days = [];
    for (let d = 0; d < stageDays; d++) {
      const tasks = [];

      // 知识模块：每天至少1个
      const modsPerDay = Math.max(1, Math.floor(stageModules.length / stageDays));
      const modStart = d * modsPerDay;
      const modEnd = d === stageDays - 1 ? stageModules.length : modStart + modsPerDay;
      for (let m = modStart; m < modEnd; m++) {
        if (stageModules[m]) {
          tasks.push({
            taskId: `task-s${si}-d${d}-m${m}`,
            type: 'knowledge',
            title: stageModules[m].name || stageModules[m],
            status: 'pending',
            content: typeof stageModules[m] === 'object' ? {
              basic: stageModules[m].basicKnowledge || '',
              keyPoints: stageModules[m].corePoints || [],
              pitfalls: stageModules[m].commonMistakes || []
            } : { basic: stageModules[m] }
          });
        }
      }

      // 习题：均匀分布
      const exPerDay = Math.ceil(stageExercises.length / stageDays);
      const exStart = d * exPerDay;
      const exEnd = Math.min(exStart + exPerDay, stageExercises.length);
      for (let e = exStart; e < exEnd; e++) {
        const ex = stageExercises[e];
        if (ex) {
          tasks.push({
            taskId: `task-s${si}-d${d}-ex${e}`,
            type: 'exercise',
            title: `练习：${(ex.question || '').substring(0, 30)}`,
            status: 'pending',
            question: ex.question,
            options: ex.options,
            answer: ex.answer,
            analysis: ex.analysis,
            difficulty: ex.difficulty,
            userAnswer: null,
            isCorrect: null
          });
        }
      }

      // 拓展阅读：可选
      const rdPerDay = Math.ceil(stageReadings.length / stageDays);
      const rdStart = d * rdPerDay;
      const rdEnd = Math.min(rdStart + rdPerDay, stageReadings.length);
      for (let r = rdStart; r < rdEnd; r++) {
        const rd = stageReadings[r];
        if (rd) {
          tasks.push({
            taskId: `task-s${si}-d${d}-rd${r}`,
            type: 'reading',
            title: rd.title || '',
            status: 'pending',
            mandatory: false,
            content: { summary: rd.summary, type: rd.type, difficulty: rd.difficulty }
          });
        }
      }

      days.push({
        dayIndex: d + 1,
        status: d === 0 ? 'active' : 'locked',
        tasks,
        totalTasks: tasks.length
      });
    }

    return {
      stageIndex: si,
      name: stage.name,
      difficulty: stage.difficulty,
      goal: stage.goal,
      totalDays: stageDays,
      status: si === 0 ? 'active' : 'locked',
      days
    };
  });

  return {
    version: 2,
    totalStages: convertedStages.length,
    totalDays: totalDaysAll,
    references: {
      graphText: planData?.graph?.textOutline || '',
      graphNodes: planData?.graph?.nodes || [],
      graphEdges: planData?.graph?.edges || []
    },
    stages: convertedStages
  };
}

/** 匹配知识模块到阶段 */
function matchModules(moduleNames, allModules) {
  return moduleNames.map(name => {
    const found = allModules.find(m => m.name === name || name.includes(m.name) || m.name.includes(name));
    return found || name;
  });
}

/** 按阶段比例分配习题/阅读 */
function distributeItems(items, stageIndex, totalStages, stageDays, totalDays) {
  if (!items?.length) return [];
  const ratio = stageDays / Math.max(1, totalDays);
  const count = Math.max(1, Math.round(items.length * ratio));
  const offset = Math.floor(items.length * stageIndex / totalStages);
  return items.slice(offset, offset + count);
}

/** 计算阶段任务完成进度 */
export function calcStageProgress(studyData) {
  if (!studyData?.stages) return 0;
  let totalMandatory = 0, completed = 0;
  for (const stage of studyData.stages) {
    for (const day of (stage.days || [])) {
      for (const task of (day.tasks || [])) {
        if (task.type !== 'reading') {
          totalMandatory++;
          if (task.status === 'completed') completed++;
        }
      }
    }
  }
  return totalMandatory > 0 ? Math.round(completed / totalMandatory * 100) : 0;
}

/** 获取当前阶段和天索引 */
export function getCurrentPosition(studyData) {
  for (let si = 0; si < (studyData?.stages?.length || 0); si++) {
    const stage = studyData.stages[si];
    if (stage.status === 'active' || stage.status === 'in_progress') {
      for (let di = 0; di < (stage.days?.length || 0); di++) {
        if (stage.days[di].status === 'active' || stage.days[di].status === 'in_progress') {
          return { stageIndex: si, dayIndex: di };
        }
      }
      return { stageIndex: si, dayIndex: 0 };
    }
  }
  return { stageIndex: 0, dayIndex: 0 };
}
