def main(key = None, format = "classic"):
    value = runtime.uuid(key)
    if format == "hexUpper":
        return runtime.replace(runtime.upper(value), "-", "")
    if format == "hexLower":
        return runtime.replace(runtime.lower(value), "-", "")
    return value
