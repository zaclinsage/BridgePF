bridge.controller('SettingsModalController', ['$http', '$humane', '$log', '$modalInstance', '$scope', 'authService', 'formService', 'modalService', 
    function($http, $humane, $log, $modalInstance, $scope, authService, formService, modalService) {

        formService.initScope($scope, 'settings');

        $http.get('/api/users/profile')
            .success(function(data, status, headers, config) {
                // These are all the fields the /api/users/profile call will need to return eventually.
                $scope.profile = {
                    // TODO Eventually need to add other fields, such as avatar image, 
                    // the ability to be asked about future studies, etc.
                    firstName: data.firstName,
                    lastName: data.lastName,
                    username: data.username,
                    email: data.email,
                };
            })
            .error($humane.status);

        $scope.session = authService;

        $scope.cancel = function() {
            $modalInstance.dismiss('cancel');
        };

        $scope.submit = function() {
            // Only two items possible to update are first name and last name.
            var update = formService.formToJSON($scope.settings, ['firstName', 'lastName']);
            $http.post('/api/users/profile', update)
                .success(function(data, status, headers, config) {
                    $modalInstance.close('success');
                    $humane.confirm('Your information has been successfully updated.');
                    $log.info(data);
                })
                .error($humane.status);
        };

        $scope.changePassword = function() {
            modalService.openModal('RequestResetPasswordModalController', 'sm', '/shared/views/requestResetPassword.html');
        };

        $scope.withdrawStudy = function() {
            $http.delete('/api/consent')
                .success(function(data, status, headers, config) {
                    $scope.setMessage('You have successfully withdrawn from the study.');
                    $scope.session.consented = false;
                })
                .error($humane.status);
        };

        $scope.emailConsent = function() {
            $http.post('/api/consent/email')
                .success(function(data, status, headers, config) {
                    $scope.setMessage('Check your email! You should be receiving a copy of the consent document shortly.');
                })
                .error($humane.status);
        };

        // TODO Not yet implemented on back end. Will need to return to this later.
        $scope.downloadData = function() {
            $log.info('Download data function has been called.');
        };
    }
]);