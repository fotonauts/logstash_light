module.exports = function(grunt) {

  // Project configuration.
  grunt.initConfig({
    pkg: grunt.file.readJSON('package.json'),
    nodemon: { dev: {
      options: {
        file: 'logstash_light.js',
        nodeArgs: ['--debug']
      }
    }
   }
  });

   grunt.loadNpmTasks('grunt-nodemon');

  // Default task(s).
  grunt.registerTask('default', ['nodemon']);

};

