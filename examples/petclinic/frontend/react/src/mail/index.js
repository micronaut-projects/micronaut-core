import React, {Component} from 'react'
import Alert from "../display/Alert"
import config from "../config"
import {shape, string} from 'prop-types'

class Mail extends Component {

  constructor() {
    super()

    this.state = {
      enabled: false,
      message: '',
      level: null,
      email: ''
    }
  }

  componentDidMount() {
    fetch(`${config.SERVER_URL}/mail/health`)
      .then(r => r.json())
      .then(json => this.setState({enabled: json.status === 'UP'}))
      .catch(e => console.warn(e))
  }

  changeEmail = (e) => this.setState({email: e.target.value})

  submitEmail = (e) => {
    e.preventDefault()
    const {email} = this.state
    const {pet} = this.props

    fetch(`${config.SERVER_URL}/mail/send`, {
      method: 'POST',
      body: JSON.stringify({email, slug: pet.slug}),
      headers: {'Content-Type': 'application/json'}
    }).then((r) => {
      r.status === 200 ?
        this.setState({email: '', message: 'Email has been sent', level: 'success'}) :
        this.setState({message: 'Could not send email', level: 'warning'})
    }).then((json) => console.log(json))
      .catch(e => console.warn(e));
  }

  displayError = () => this.setState({message: 'Enter a valid email address', level: 'warning'})

  render() {
    const {message, level, email, enabled} = this.state
    const {pet} = this.props;

    const valid =
      email.length > 0 &&
      email.match(/^([\w.%+-]+)@([\w-]+\.)+([\w]{2,})$/i);

    return enabled ? <div>
      <div className="jumbotron">
        <h4>Request Info about {pet.name}</h4>
        <form className="form-group" onSubmit={valid ? this.submitEmail : this.displayError}>
          <label htmlFor="inputEmail">Email address</label>
          <input type="email" className="form-control" name="email" id="inputEmail" placeholder="Enter email"
                 onChange={this.changeEmail} value={email}/>
          <small id="emailHelp" className="form-text text-muted">We'll never share your email with anyone else.</small>
          <br/>
          <input type='submit' className={`btn btn-primary ${valid ? '' : 'disabled'}`} value='Send me info'/>
        </form>

      </div>
      <Alert message={message} level={level}/>
    </div> : null
  }
}

Mail.propTypes = {
  pet: shape({
    name: string
  })
}

export default Mail
