import React from 'react'
import {string} from 'prop-types'

const Alert = ({message, level}) => message ? <div className={`alert alert-${level ? level: 'info'} alert-dismissible fade show`} role="alert">
  {message}
  <button type="button" className="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">×</span>
  </button>
</div> : null

Alert.propTypes = {
  message: string,
  level: string.isRequired
}

export default Alert;