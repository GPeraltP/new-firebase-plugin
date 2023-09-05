#import "FirebasePlugin.h"
#import "AppDelegate+FirebasePlugin.h"
#import <Cordova/CDV.h>
#import "AppDelegate.h"
@import Firebase;
@import FirebaseAnalytics;
@import FirebasePerformance;

#if defined(__IPHONE_10_0) && __IPHONE_OS_VERSION_MAX_ALLOWED >= __IPHONE_10_0
@import UserNotifications;
#endif

#ifndef NSFoundationVersionNumber_iOS_9_x_Max
#define NSFoundationVersionNumber_iOS_9_x_Max 1299
#endif

@implementation FirebasePlugin

@synthesize notificationCallbackId;
@synthesize tokenRefreshCallbackId;
@synthesize notificationStack;
@synthesize traces;

static NSInteger const kNotificationStackSize = 10;
static FirebasePlugin *firebasePlugin;

+ (FirebasePlugin *) firebasePlugin {
    return firebasePlugin;
}

- (void)pluginInitialize {
    NSLog(@"FirebasePlugin - Starting Firebase plugin");
    firebasePlugin = self;
}

//
// Notifications
//
- (void)getId:(CDVInvokedUrlCommand *)command {
  __block CDVPluginResult *pluginResult;

  FIRInstanceIDHandler handler = ^(NSString *_Nullable instID, NSError *_Nullable error) {
    if (error) {
      pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
    } else {
      pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:instID];
    }

    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
  };

  [[FIRInstanceID instanceID] getIDWithHandler:handler];
}

- (void)getToken:(CDVInvokedUrlCommand *)command {
    [[FIRInstanceID instanceID] instanceIDWithHandler:^(FIRInstanceIDResult * _Nullable result, NSError * _Nullable error) {
        NSString* token = nil;
        if (error == nil && result != nil && result.token != nil) {
            token = result.token;
        }
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: token];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)hasPermission:(CDVInvokedUrlCommand *)command {
    BOOL enabled = NO;
    UIApplication *application = [UIApplication sharedApplication];

    if ([[UIApplication sharedApplication] respondsToSelector:@selector(registerUserNotificationSettings:)]) {
        enabled = application.currentUserNotificationSettings.types != UIUserNotificationTypeNone;
    } else {
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
        enabled = application.enabledRemoteNotificationTypes != UIRemoteNotificationTypeNone;
#pragma GCC diagnostic pop
    }

    NSMutableDictionary* message = [NSMutableDictionary dictionaryWithCapacity:1];
    [message setObject:[NSNumber numberWithBool:enabled] forKey:@"isEnabled"];
    CDVPluginResult *commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:message];
    [self.commandDelegate sendPluginResult:commandResult callbackId:command.callbackId];
}

- (void)grantPermission:(CDVInvokedUrlCommand *)command {
  if ([UNUserNotificationCenter class] != nil) {
    // iOS 10 or higher
    [UNUserNotificationCenter currentNotificationCenter].delegate = self;
    UNAuthorizationOptions authOptions = UNAuthorizationOptionAlert | UNAuthorizationOptionSound | UNAuthorizationOptionBadge;
    [[UNUserNotificationCenter currentNotificationCenter]
      requestAuthorizationWithOptions:authOptions
      completionHandler:^(BOOL granted, NSError * _Nullable error) {
        [[UIApplication sharedApplication] registerForRemoteNotifications];
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus: granted ? CDVCommandStatus_OK : CDVCommandStatus_ERROR];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
  } else {
    // iOS 10 notifications aren't available
    // fall back to iOS 8-9 notifications
    UIUserNotificationType allNotificationTypes = (UIUserNotificationTypeSound | UIUserNotificationTypeAlert | UIUserNotificationTypeBadge);
    UIUserNotificationSettings *settings = [UIUserNotificationSettings settingsForTypes:allNotificationTypes categories:nil];
    [[UIApplication sharedApplication] registerUserNotificationSettings:settings];
    [[UIApplication sharedApplication] registerForRemoteNotifications];
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
  }
  return;
}

- (void)setBadgeNumber:(CDVInvokedUrlCommand *)command {
    int number = [[command.arguments objectAtIndex:0] intValue];

    [self.commandDelegate runInBackground:^{
        [[UIApplication sharedApplication] setApplicationIconBadgeNumber:number];

        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)getBadgeNumber:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        long badge = [[UIApplication sharedApplication] applicationIconBadgeNumber];

        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDouble:badge];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)subscribe:(CDVInvokedUrlCommand *)command {
    NSString* topic = [NSString stringWithFormat:@"/topics/%@", [command.arguments objectAtIndex:0]];

    [[FIRMessaging messaging] subscribeToTopic: topic];

    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)unsubscribe:(CDVInvokedUrlCommand *)command {
    NSString* topic = [NSString stringWithFormat:@"/topics/%@", [command.arguments objectAtIndex:0]];

    [[FIRMessaging messaging] unsubscribeFromTopic: topic];

    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)unregister:(CDVInvokedUrlCommand *)command {
    [[FIRInstanceID instanceID] deleteIDWithHandler:^void(NSError *_Nullable error) {
        if (error) {
            NSLog(@"FirebasePlugin - Unable to delete instance");
        } else {
            [[FIRInstanceID instanceID] instanceIDWithHandler:^(FIRInstanceIDResult * _Nullable result, NSError * _Nullable error) {
                NSString* token = nil;
                if (error == nil && result != nil && result.token != nil) {
                    token = result.token;
                }
                if (token != nil) {
                    [self sendToken:token];
                }
                CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            }];

            //CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
            //[self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
    }];
}

- (void)onNotificationOpen:(CDVInvokedUrlCommand *)command {
    self.notificationCallbackId = command.callbackId;

    if (self.notificationStack != nil && [self.notificationStack count]) {
        for (NSDictionary *userInfo in self.notificationStack) {
            [self sendNotification:userInfo];
        }
        [self.notificationStack removeAllObjects];
    }
}

- (void)onTokenRefresh:(CDVInvokedUrlCommand *)command {
    self.tokenRefreshCallbackId = command.callbackId;
    [[FIRInstanceID instanceID] instanceIDWithHandler:^(FIRInstanceIDResult * _Nullable result, NSError * _Nullable error) {
        NSString* token = nil;
        if (error == nil && result != nil && result.token != nil) {
            token = result.token;
        }
        if (token != nil) {
            [self sendToken:token];
        }
    }];
}

- (void)sendNotification:(NSDictionary *)userInfo {
    if (self.notificationCallbackId != nil) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:userInfo];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.notificationCallbackId];
    } else {
        if (!self.notificationStack) {
            self.notificationStack = [[NSMutableArray alloc] init];
        }

        // stack notifications until a callback has been registered
        [self.notificationStack addObject:userInfo];

        if ([self.notificationStack count] >= kNotificationStackSize) {
            [self.notificationStack removeLastObject];
        }
    }
}

- (void)sendToken:(NSString *)token {
    if (self.tokenRefreshCallbackId != nil) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:token];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.tokenRefreshCallbackId];
    }
}

- (void)clearAllNotifications:(CDVInvokedUrlCommand *)command {
	[self.commandDelegate runInBackground:^{
        [[UIApplication sharedApplication] setApplicationIconBadgeNumber:1];
        [[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];

        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

//
// Analytics
//
- (void)setAnalyticsCollectionEnabled:(CDVInvokedUrlCommand *)command {
     [self.commandDelegate runInBackground:^{
        BOOL enabled = [[command argumentAtIndex:0] boolValue];

        [FIRAnalytics setAnalyticsCollectionEnabled:enabled];
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
     }];
}

- (void)logEvent:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        NSString* name = [command.arguments objectAtIndex:0];
        NSDictionary *parameters;
        @try {
            NSString *description = NSLocalizedString([command argumentAtIndex:1 withDefault:@"No Message Provided"], nil);
            parameters = @{ NSLocalizedDescriptionKey: description };
        }
        @catch (NSException *execption) {
            parameters = [command argumentAtIndex:1];
        }

        [FIRAnalytics logEventWithName:name parameters:parameters];

        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)setScreenName:(CDVInvokedUrlCommand *)command {
    NSString* name = [command.arguments objectAtIndex:0];

    [FIRAnalytics setScreenName:name screenClass:NULL];
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)setUserId:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        NSString* id = [command.arguments objectAtIndex:0];

        [FIRAnalytics setUserID:id];

        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)setUserProperty:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        NSString* name = [command.arguments objectAtIndex:0];
        NSString* value = [command.arguments objectAtIndex:1];

        [FIRAnalytics setUserPropertyString:value forName:name];

        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

//
// Crashlytics
//
- (void)logError:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        NSString* errorMessage = [command.arguments objectAtIndex:0];
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)setCrashlyticsUserId:(CDVInvokedUrlCommand *)command {
    NSString* userId = [command.arguments objectAtIndex:0];

    [[FIRCrashlytics crashlytics] setUserID:userId];
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)forceCrashlytics:(CDVInvokedUrlCommand *)command {
    //[[Crashlytics sharedInstance] crash];
}

//
// Remote Config
//
- (void)fetch:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        FIRRemoteConfig* remoteConfig = [FIRRemoteConfig remoteConfig];

        if ([command.arguments count] > 0) {
            int expirationDuration = [[command.arguments objectAtIndex:0] intValue];

            [remoteConfig fetchWithExpirationDuration:expirationDuration completionHandler:^(FIRRemoteConfigFetchStatus status, NSError * _Nullable error) {
                if (status == FIRRemoteConfigFetchStatusSuccess) {
                    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
                    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                }
            }];
        } else {
            [remoteConfig fetchWithCompletionHandler:^(FIRRemoteConfigFetchStatus status, NSError * _Nullable error) {
                if (status == FIRRemoteConfigFetchStatusSuccess) {
                    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
                    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                }
            }];
        }
    }];
}

- (void)activateFetched:(CDVInvokedUrlCommand *)command {
     [self.commandDelegate runInBackground:^{
        FIRRemoteConfig* remoteConfig = [FIRRemoteConfig remoteConfig];
         
         CDVPluginResult *pluginResult;
       
         @try {
             FIRRemoteConfig* remoteConfig = [FIRRemoteConfig remoteConfig];
             [remoteConfig activateWithCompletion:^(BOOL changed, NSError* _Nullable error) {
               	CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
             	[self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
             }];
         }@catch (NSException *exception) {
             CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
           	 [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
         }
     }];
}

- (void)getValue:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        NSString* key = [command.arguments objectAtIndex:0];
        FIRRemoteConfig* remoteConfig = [FIRRemoteConfig remoteConfig];
        NSString* value = remoteConfig[key].stringValue;
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:value];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

//
// Performace
//
- (void)startTrace:(CDVInvokedUrlCommand *)command {

    [self.commandDelegate runInBackground:^{
        NSString* traceName = [command.arguments objectAtIndex:0];
        FIRTrace *trace = [self.traces objectForKey:traceName];

        if (self.traces == nil) {
            self.traces = [NSMutableDictionary new];
        }

        if (trace == nil) {
            trace = [FIRPerformance startTraceWithName:traceName];
            [self.traces setObject:trace forKey:traceName ];

        }

        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];

    }];
}

- (void)incrementCounter:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        NSString* traceName = [command.arguments objectAtIndex:0];
        NSString* counterNamed = [command.arguments objectAtIndex:1];
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        FIRTrace *trace = (FIRTrace*)[self.traces objectForKey:traceName];

        if (trace != nil) {
            [trace incrementMetric:counterNamed byInt:1];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        } else {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Trace not found"];
        }

        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];

    }];
}

- (void)stopTrace:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        NSString* traceName = [command.arguments objectAtIndex:0];
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        FIRTrace *trace = [self.traces objectForKey:traceName];

        if (trace != nil) {
            [trace stop];
            [self.traces removeObjectForKey:traceName];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        } else {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Trace not found"];
        }

        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];

    }];
}

- (void)setPerformanceCollectionEnabled:(CDVInvokedUrlCommand *)command {
     [self.commandDelegate runInBackground:^{
         BOOL enabled = [[command argumentAtIndex:0] boolValue];

         [[FIRPerformance sharedInstance] setDataCollectionEnabled:enabled];

         CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];

         [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
     }];
}

/*
//
// Authentication
//

- (void)authCustomToken:(CDVInvokedUrlCommand *)command {
    NSString* mCustomToken = [command.arguments objectAtIndex:0];

    [[FIRAuth auth] signInWithCustomToken:mCustomToken completion:^(FIRUser * _Nullable user, NSError * _Nullable error) {
        CDVPluginResult *pluginResult;
        if (error){
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Authentication failed."];
        }else{
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Authentication successful."];
        }
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)signOut:(CDVInvokedUrlCommand *)command {
        NSError *signOutError;
        BOOL status = [[FIRAuth auth] signOut:&signOutError];
        if (!status) {
        //NSLog(@"Error signing out: %@", signOutError);
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Error signing out"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
}


//
// Database Realtime
//

- (void)writeUsers:(CDVInvokedUrlCommand *)command {
    NSString *phoneNumber = [command.arguments objectAtIndex:0];
    NSString *registerDate = [command.arguments objectAtIndex:1];
    NSString *contractNumber = [command.arguments objectAtIndex:2];
    NSString *key = [[[[[FIRDatabase database] reference] child:@"users"] child:contractNumber] key];
    NSDictionary *user = @{
                        @"phoneNumber": phoneNumber,
                        @"registerDate": registerDate
                        };
    [[[[[FIRDatabase database] reference] child:@"users"] child:key] setValue:user withCompletionBlock:^(NSError * _Nullable error, FIRDatabaseReference * _Nonnull ref) {
        CDVPluginResult *pluginResult;
        if (error) {
            //NSLog(@"Data could not be saved: %@", error);
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Data could not be saved."];
        } else {
            //NSLog(@"Data saved successfully.");
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Data saved successfully."];
        }
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)writeDate:(CDVInvokedUrlCommand *)command {
    NSString *date = [command.arguments objectAtIndex:0];
    NSString *contractNumber = [command.arguments objectAtIndex:1];
    [[[[[FIRDatabase database] reference] child: @"reviews"] child:contractNumber] setValue:date withCompletionBlock:^(NSError * _Nullable error, FIRDatabaseReference * _Nonnull ref) {
        CDVPluginResult *pluginResult;
        if (error) {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Data could not be saved."];
        } else {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Data Review saved successfully."];
        }
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)writeReviews:(CDVInvokedUrlCommand *)command {
    NSString *calification = [command.arguments objectAtIndex:0];
    NSString *date = [command.arguments objectAtIndex:1];
    NSString *contractNumber = [command.arguments objectAtIndex:2];
    NSString *key = [[[[[[FIRDatabase database] reference] child:@"reviews"] child:contractNumber] childByAutoId] key];
    NSDictionary *review = @{
                            @"calification":calification,
                            @"date":date
                            };
     [[[[[[FIRDatabase database] reference] child: @"reviews"] child:contractNumber] child:key] setValue:review withCompletionBlock:^(NSError * _Nullable error, FIRDatabaseReference * _Nonnull ref) {
        CDVPluginResult *pluginResult;
        if (error) {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Data could not be saved."];
        } else {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Data Review saved successfully."];
        }
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)writeReviewsWithComment:(CDVInvokedUrlCommand *)command {
    NSString *calification = [command.arguments objectAtIndex:0];
    NSString *comment = [command.arguments objectAtIndex:1];
    NSString *date = [command.arguments objectAtIndex:2];
    NSString *contractNumber = [command.arguments objectAtIndex:3];
    NSString *key = [[[[[[FIRDatabase database] reference] child:@"reviews"] child:contractNumber] childByAutoId] key];
    NSDictionary *review = @{
                            @"calification":calification,
                            @"comment":comment,
                            @"date":date
                            };
    [[[[[[FIRDatabase database] reference] child: @"reviews"] child:contractNumber] child:key] setValue:review withCompletionBlock:^(NSError * _Nullable error, FIRDatabaseReference * _Nonnull ref) {
        CDVPluginResult *pluginResult;
        if (error) {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Data could not be saved."];
        } else {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Data Review saved successfully."];
        }
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)userExist:(CDVInvokedUrlCommand *)command {
    NSString *contractNumber = [command.arguments objectAtIndex:0];
    [[[[[FIRDatabase database] reference] child:@"users"] child:contractNumber] observeSingleEventOfType:FIRDataEventTypeValue withBlock:^(FIRDataSnapshot * _Nonnull snapshot) {
        CDVPluginResult *pluginResult;
        if(snapshot.exists){
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"True"];
        }else{
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"False"];
        }
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    } withCancelBlock:^(NSError * _Nonnull error) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Error getting result."];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}
*/

@end
