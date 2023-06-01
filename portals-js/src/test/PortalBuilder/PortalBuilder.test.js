// Invoke ApplicationBuilder directly
let applicationBuilder = PortalsJS.ApplicationBuilder('PortalBuilder');

// Assuming that portal is a function that returns an instance of PortalBuilderJS
let portalBuilder = applicationBuilder.portal;

// Test portal method
try {
    let portalResult = portalBuilder.portal("test_portal");
    console.log('portal method executed successfully. Result:', portalResult);
} catch (error) {
    console.error('Error when executing portal method:', error);
}

let f = function(x) { return x * 5; }; // Function1JS instance
try {
    let portalResult = portalBuilder.portal("test_portal", f);
    console.log('portal method with function executed successfully. Result:', portalResult);
} catch (error) {
    console.error('Error when executing portal method with function:', error);
}
