// Invoke ApplicationBuilder directly
let applicationBuilder = PortalsJS.ApplicationBuilder('Application');

// Test build method
try {
    let buildResult = applicationBuilder.build();
    console.log('build method executed successfully. Result:', buildResult);
} catch (error) {
    console.error('Error when executing build method:', error);
}

// Test registry method
try {
    let registryResult = applicationBuilder.registry;
    console.log('registry method executed successfully. Result:', registryResult);
} catch (error) {
    console.error('Error when executing registry method:', error);
}

// Test workflows method
try {
    let workflowsResult = applicationBuilder.workflows;
    console.log('workflows method executed successfully. Result:', workflowsResult);
} catch (error) {
    console.error('Error when executing workflows method:', error);
}

// Test splitters method
try {
    let splittersResult = applicationBuilder.splitters;
    console.log('splitters method executed successfully. Result:', splittersResult);
} catch (error) {
    console.error('Error when executing splitters method:', error);
}

// Test splits method
try {
    let splitsResult = applicationBuilder.splits;
    console.log('splits method executed successfully. Result:', splitsResult);
} catch (error) {
    console.error('Error when executing splits method:', error);
}

// Test generators method
try {
    let generatorsResult = applicationBuilder.generators;
    console.log('generators method executed successfully. Result:', generatorsResult);
} catch (error) {
    console.error('Error when executing generators method:', error);
}

// Test sequencers method
try {
    let sequencersResult = applicationBuilder.sequencers;
    console.log('sequencers method executed successfully. Result:', sequencersResult);
} catch (error) {
    console.error('Error when executing sequencers method:', error);
}

// Test connections method
try {
    let connectionsResult = applicationBuilder.connections;
    console.log('connections method executed successfully. Result:', connectionsResult);
} catch (error) {
    console.error('Error when executing connections method:', error);
}

// Test portal method
try {
    let portalResult = applicationBuilder.portal;
    console.log('portal method executed successfully. Result:', portalResult);
} catch (error) {
    console.error('Error when executing portal method:', error);
}

// Test tasks method
try {
    let tasksResult = applicationBuilder.tasks;
    console.log('tasks method executed successfully. Result:', tasksResult);
} catch (error) {
    console.error('Error when executing tasks method:', error);
}
