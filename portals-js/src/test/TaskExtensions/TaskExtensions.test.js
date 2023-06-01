// Assuming that we have a valid StatefulTaskContext object
let statefulTaskContext = PortalsJS.StatefulTaskContext('test_stateful_task_context');

// Test PerTaskStateJS class
try {
    let perTaskState = PortalsJS.PerTaskState('test_task_state', 0, statefulTaskContext);
    perTaskState.set(10);
    let value = perTaskState.get();
    console.log('PerTaskStateJS set and get methods executed successfully. Value:', value);
    perTaskState.del();
    console.log('PerTaskStateJS del method executed successfully.');
} catch (error) {
    console.error('Error when executing PerTaskStateJS methods:', error);
}

// Test PerKeyStateJS class
try {
    let perKeyState = PortalsJS.PerKeyState('test_key_state', 0, statefulTaskContext);
    perKeyState.set(10);
    let value = perKeyState.get();
    console.log('PerKeyStateJS set and get methods executed successfully. Value:', value);
    perKeyState.del();
    console.log('PerKeyStateJS del method executed successfully.');
} catch (error) {
    console.error('Error when executing PerKeyStateJS methods:', error);
}
