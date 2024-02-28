def configurations = [
  [ platform: "linux", jdk: "21" ],
  [ platform: "windows", jdk: "17" ]
]

def params = [
    failFast: false,
    configurations: configurations,
    checkstyle: [qualityGates: [[threshold: 1, type: 'NEW', unstable: true]]],
    spotbugs: [qualityGates: [[threshold: 1, type: 'NEW', unstable: true]]]
]

buildPlugin(params)
