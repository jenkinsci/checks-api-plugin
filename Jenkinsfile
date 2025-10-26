def configurations = [
  [ platform: "linux", jdk: 25 ],
  [ platform: "windows", jdk: 21 ]
]

def params = [
    failFast: false,
    configurations: configurations,
    checkstyle: [qualityGates: [[threshold: 1, type: 'NEW', unstable: true]]],
    spotbugs: [qualityGates: [[threshold: 1, type: 'NEW', unstable: true]]]
]

buildPlugin(params)
