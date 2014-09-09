bridge.service('healthDataService', ['$http', '$rootScope', '$q', function($http, $rootScope, $q) {

    var service = {
        getAll: function(trackerId) {
            var url = '/api/v1/healthdata/'+trackerId;
            return $http.get(url);
        },
        getByDateRange: function(trackerId, startDate, endDate) {
            startDate = new Date(startDate).toISOString();
            endDate = new Date(endDate).toISOString();
            var url = '/api/v1/healthdata/'+trackerId+'?startDate='+startDate+'&endDate='+endDate;
            return $http.get(url);
        },
        get: function(trackerId, recordId) {
            var url = '/api/v1/healthdata/'+trackerId+"/record/"+recordId;
            return $http.get(url);
        },
        create: function(trackerId, object) {
            if (object.recordId) {
                throw new Error("Trying to create a record with a pre-existing recordId");
            }
            var url = '/api/v1/healthdata/'+trackerId;
            return $http.post(url, JSON.stringify([object]));
        },
        update: function(trackerId, object) {
            if (!object.recordId) {
                throw new Error("Cannot update a record with no recordId");
            } else if (typeof object.version === "undefined") {
                throw new Error("Cannot update a record with no version");
            }
            var url = '/api/v1/healthdata/'+trackerId+'/record/'+object.recordId;
            return $http.post(url, JSON.stringify(object));
        },
        remove: function(trackerId, recordId) {
            var url = '/api/v1/healthdata/'+trackerId+'/record/'+recordId;
            return $http['delete'](url);
        },
        createPayload: function(form, dateFields, fields, toMidnight) {
            toMidnight = (typeof toMidnight === "boolean") ? toMidnight : false;
            var startDate = form[dateFields[0]].$modelValue;
            if (toMidnight) {
                startDate.setHours(0,0,0,0);
                startDate = startDate.toISOString();
            }
            var endDate = form[dateFields[0]].$modelValue;
            if (toMidnight) {
                endDate.setHours(0,0,0,0);
                endDate = endDate.toISOString();
            }
            var payload = { startDate: startDate, endDate: endDate, data: {} };
            fields.forEach(function(field) {
                payload.data[field] = form[field].$modelValue;
            });
            return payload;
        },
        updateRecord: function(record, form, dateFields, fields) {
            record.startDate = form[dateFields[0]].$modelValue.getTime();
            record.endDate = form[dateFields[1]].$modelValue.getTime();
            fields.forEach(function(field) {
                record.data[field] = form[field].$modelValue;
            });
            delete record.$$hashKey; // oh Angular
            return record;
        }
    };
    return service;
}]);