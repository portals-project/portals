// Invoke ApplicationBuilder directly
let applicationBuilder = PortalsJS.ApplicationBuilder('WorfklowBuilder');

// Assuming that workflows is a function that returns an instance of WorkflowBuilderJS
let workflowBuilder = applicationBuilder.workflows;

// Test freeze method
try {
    let freezeResult = workflowBuilder.freeze();
    console.log('freeze method executed successfully. Result:', freezeResult);
} catch (error) {
    console.error('Error when executing freeze method:', error);
}

// Test source method
try {
    let sourceResult = workflowBuilder.source(applicationBuilder.generators.fromRange(0, 128, 8).stream);
    console.log('source method executed successfully. Result:', sourceResult);
} catch (error) {
    console.error('Error when executing source method:', error);
}
